package com.adityakumar.walletledger;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WalletApiTest {

    @Autowired MockMvc mvc;

    private static String key() {
        return "k-" + UUID.randomUUID();
    }

    private long createWallet(String owner, String currency) throws Exception {
        String body = currency == null
                ? "{\"ownerName\":\"" + owner + "\"}"
                : "{\"ownerName\":\"" + owner + "\",\"currency\":\"" + currency + "\"}";
        String response = mvc.perform(post("/api/wallets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private MockHttpServletRequestBuilder topUp(long walletId, String key, String amount) {
        return post("/api/wallets/" + walletId + "/topups").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}");
    }

    private MockHttpServletRequestBuilder transfer(long from, long to, String key, String amount) {
        return post("/api/transfers").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromWalletId\":" + from + ",\"toWalletId\":" + to + ",\"amount\":" + amount + "}");
    }

    @Test
    void newWalletStartsEmptyInInrByDefault() throws Exception {
        long id = createWallet("Asha", null);
        mvc.perform(get("/api/wallets/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.ownerName").value("Asha"));
    }

    @Test
    void walletValidation() throws Exception {
        mvc.perform(post("/api/wallets").contentType(MediaType.APPLICATION_JSON).content("{\"ownerName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ownerName").exists());
        mvc.perform(post("/api/wallets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"A\",\"currency\":\"rupees\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
        mvc.perform(get("/api/wallets/999999")).andExpect(status().isNotFound());
    }

    @Test
    void topUpCreditsTheWalletAndWritesALedgerEntry() throws Exception {
        long id = createWallet("Ravi", null);
        mvc.perform(topUp(id, key(), "250.50"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.type").value("TOPUP"))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].type").value("CREDIT"))
                .andExpect(jsonPath("$.entries[0].balanceAfter").value(250.50));
        mvc.perform(get("/api/wallets/" + id)).andExpect(jsonPath("$.balance").value(250.50));
    }

    @Test
    void sameKeyAndRequestIsAppliedOnlyOnce() throws Exception {
        long id = createWallet("Meera", null);
        String key = key();

        String first = mvc.perform(topUp(id, key, "100.00")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(topUp(id, key, "100.00")).andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
                ((Number) JsonPath.read(first, "$.id")).longValue(),
                ((Number) JsonPath.read(second, "$.id")).longValue());
        mvc.perform(get("/api/wallets/" + id)).andExpect(jsonPath("$.balance").value(100.00));
        mvc.perform(get("/api/wallets/" + id + "/ledger")).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void sameKeyWithADifferentRequestIsRejected() throws Exception {
        long id = createWallet("Kabir", null);
        String key = key();
        mvc.perform(topUp(id, key, "100.00")).andExpect(status().isCreated());

        mvc.perform(topUp(id, key, "999.00")).andExpect(status().isConflict());
        mvc.perform(get("/api/wallets/" + id)).andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void transferMovesMoneyAndWritesBothSidesOfTheLedger() throws Exception {
        long from = createWallet("Sender", null);
        long to = createWallet("Receiver", null);
        mvc.perform(topUp(from, key(), "500.00")).andExpect(status().isCreated());

        String response = mvc.perform(transfer(from, to, key(), "120.25"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].balanceAfter").value(379.75))
                .andExpect(jsonPath("$.entries[1].type").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].balanceAfter").value(120.25))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/wallets/" + from)).andExpect(jsonPath("$.balance").value(379.75));
        mvc.perform(get("/api/wallets/" + to)).andExpect(jsonPath("$.balance").value(120.25));
        long txId = ((Number) JsonPath.read(response, "$.id")).longValue();
        mvc.perform(get("/api/transactions/" + txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2));
    }

    @Test
    void insufficientFundsIsRejectedAndNothingChanges() throws Exception {
        long from = createWallet("Poor", null);
        long to = createWallet("Rich", null);
        mvc.perform(topUp(from, key(), "50.00")).andExpect(status().isCreated());

        mvc.perform(transfer(from, to, key(), "50.01")).andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/wallets/" + from)).andExpect(jsonPath("$.balance").value(50.00));
        mvc.perform(get("/api/wallets/" + to)).andExpect(jsonPath("$.balance").value(0));
        mvc.perform(get("/api/wallets/" + from + "/ledger")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/wallets/" + to + "/ledger")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aFailedTransferDoesNotUseUpItsKey() throws Exception {
        long from = createWallet("A", null);
        long to = createWallet("B", null);
        String key = key();
        mvc.perform(transfer(from, to, key, "10.00")).andExpect(status().isUnprocessableEntity());

        mvc.perform(topUp(from, key(), "10.00")).andExpect(status().isCreated());
        mvc.perform(transfer(from, to, key, "10.00")).andExpect(status().isCreated());
    }

    @Test
    void transferRulesAreEnforced() throws Exception {
        long inr = createWallet("Indian", "INR");
        long usd = createWallet("American", "USD");
        mvc.perform(topUp(inr, key(), "100.00")).andExpect(status().isCreated());

        mvc.perform(transfer(inr, inr, key(), "1.00")).andExpect(status().isBadRequest());
        mvc.perform(transfer(inr, usd, key(), "1.00")).andExpect(status().isUnprocessableEntity());
        mvc.perform(transfer(inr, 999999, key(), "1.00")).andExpect(status().isNotFound());
        mvc.perform(transfer(999999, inr, key(), "1.00")).andExpect(status().isNotFound());
        mvc.perform(get("/api/wallets/" + inr)).andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void badAmountsAndKeysAreRejected() throws Exception {
        long id = createWallet("Validation", null);
        for (String amount : new String[]{"0", "-5", "0.001", "10.123", "\"abc\""}) {
            mvc.perform(topUp(id, key(), amount)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/wallets/" + id + "/topups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(topUp(id, "bad key!", "5")).andExpect(status().isBadRequest());
        mvc.perform(topUp(id, "x".repeat(65), "5")).andExpect(status().isBadRequest());
        mvc.perform(topUp(999999, key(), "5")).andExpect(status().isNotFound());
        mvc.perform(get("/api/wallets/" + id)).andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void ledgerIsPagedNewestFirstAndReconciles() throws Exception {
        long id = createWallet("History", null);
        for (int i = 1; i <= 5; i++) {
            mvc.perform(topUp(id, key(), String.valueOf(i * 10))).andExpect(status().isCreated());
        }

        mvc.perform(get("/api/wallets/" + id + "/ledger?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(150))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(100));

        mvc.perform(get("/api/wallets/" + id + "/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150))
                .andExpect(jsonPath("$.ledgerBalance").value(150))
                .andExpect(jsonPath("$.consistent").value(true));
        mvc.perform(get("/api/wallets/999999/ledger")).andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions/999999")).andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
