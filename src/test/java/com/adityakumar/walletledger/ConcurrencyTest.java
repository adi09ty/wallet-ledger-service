package com.adityakumar.walletledger;

import com.adityakumar.walletledger.dto.Dtos.CreateWalletRequest;
import com.adityakumar.walletledger.dto.Dtos.TopUpRequest;
import com.adityakumar.walletledger.dto.Dtos.TransferRequest;
import com.adityakumar.walletledger.exception.InsufficientFundsException;
import com.adityakumar.walletledger.service.OperationResult;
import com.adityakumar.walletledger.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real threads against a real database (no test-managed transaction), because these guarantees only mean
 * something under genuine concurrency.
 */
@SpringBootTest
class ConcurrencyTest {

    @Autowired WalletService service;

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static String key() {
        return "c-" + UUID.randomUUID();
    }

    private long newWallet(String owner, String initialBalance) {
        long id = service.createWallet(new CreateWalletRequest(owner, null)).id();
        if (initialBalance != null) {
            service.topUp(key(), id, new TopUpRequest(money(initialBalance), null));
        }
        return id;
    }

    /** Runs all tasks at the same moment on separate threads and returns their futures. */
    private <T> List<Future<T>> runTogether(List<Callable<T>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS);
        return futures;
    }

    @Test
    void identicalRequestsSentAtTheSameMomentAreAppliedExactlyOnce() throws Exception {
        long wallet = newWallet("Racer", null);
        String key = key();
        List<Callable<OperationResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> service.topUp(key, wallet, new TopUpRequest(money("100.00"), null)));
        }

        List<Future<OperationResult>> futures = runTogether(tasks);

        Set<Long> transactionIds = futures.stream().map(f -> {
            try {
                return f.get().transaction().getId();
            } catch (InterruptedException | ExecutionException e) {
                throw new AssertionError("a duplicate request failed instead of returning the original result", e);
            }
        }).collect(Collectors.toSet());

        assertEquals(1, transactionIds.size(), "every caller must get the same transaction");
        assertEquals(0, money("100.00").compareTo(service.getWallet(wallet).balance()), "credited once, not ten times");
        assertEquals(1, service.ledger(wallet, org.springframework.data.domain.PageRequest.of(0, 50)).totalElements());
    }

    @Test
    void aWalletCanNeverBeOverdrawnByParallelTransfers() throws Exception {
        long source = newWallet("Source", "100.00");
        long target = newWallet("Target", null);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                try {
                    service.transfer(key(), new TransferRequest(source, target, money("30.00"), null));
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    rejected.incrementAndGet();
                }
                return null;
            });
        }
        for (Future<Void> f : runTogether(tasks)) {
            f.get();
        }

        assertEquals(3, succeeded.get(), "100.00 pays for exactly three transfers of 30.00");
        assertEquals(7, rejected.get());
        assertEquals(0, money("10.00").compareTo(service.getWallet(source).balance()));
        assertEquals(0, money("90.00").compareTo(service.getWallet(target).balance()));
    }

    @Test
    void manyOpposingTransfersConserveMoneyAndNeverDeadlock() throws Exception {
        long a = newWallet("A", "1000.00");
        long b = newWallet("B", "1000.00");

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            boolean aToB = t % 2 == 0;
            tasks.add(() -> {
                for (int i = 0; i < 15; i++) {
                    long from = aToB ? a : b;
                    long to = aToB ? b : a;
                    service.transfer(key(), new TransferRequest(from, to, money("7.00"), null));
                }
                return null;
            });
        }
        for (Future<Void> f : runTogether(tasks)) {
            f.get();
        }

        BigDecimal total = service.getWallet(a).balance().add(service.getWallet(b).balance());
        assertEquals(0, money("2000.00").compareTo(total), "money must be neither created nor destroyed");
        assertTrue(service.reconcile(a).consistent(), "wallet A balance must equal its ledger");
        assertTrue(service.reconcile(b).consistent(), "wallet B balance must equal its ledger");
        assertEquals(1 + 8 * 15, service.ledger(a, org.springframework.data.domain.PageRequest.of(0, 500)).totalElements());
    }
}
