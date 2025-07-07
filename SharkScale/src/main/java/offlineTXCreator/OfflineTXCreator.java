package offlineTXCreator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import offlineWallet.GetWallet;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;

import java.io.*;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class OfflineTXCreator implements INetworkConnection, ITXCreator {

    private final ArrayList<TransactionJob> pendingTransactionJobs;
    private final GetWallet getWallet;
    private BigInteger currentNonce;
    private final Web3j web3j;
    private final String walletAddress;

    public OfflineTXCreator(GetWallet getWallet, Web3j web3j) throws IOException, InterruptedException, ExecutionException {
        if (getWallet == null) throw new IllegalArgumentException("Wallet-Provider darf nicht null sein");
        if (web3j == null) throw new IllegalArgumentException("Web3j darf nicht null sein");
        this.getWallet = getWallet;
        this.web3j = web3j;
        this.pendingTransactionJobs = new ArrayList<>();
        this.walletAddress = getWallet.getCredentials().getAddress();
        this.resyncNonce();
    }

    public OfflineTXCreator(GetWallet getWallet) throws IOException, InterruptedException, ExecutionException {
        this(getWallet, Web3j.build(new HttpService("https://sepolia.drpc.org")));
    }

    public OfflineTXCreator(GetWallet getWallet, Web3j web3j, BigInteger initialNonce) {
        if (getWallet == null) throw new IllegalArgumentException("Wallet-Provider darf nicht null sein");
        if (web3j == null) throw new IllegalArgumentException("Web3j darf nicht null sein");
        if (initialNonce == null || initialNonce.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Anfängliche Nonce darf nicht null oder negativ sein");
        }
        this.getWallet = getWallet;
        this.web3j = web3j;
        this.pendingTransactionJobs = new ArrayList<>();
        this.walletAddress = getWallet.getCredentials().getAddress();
        this.currentNonce = initialNonce;
    }

    public BigInteger getCurrentNonce() {
        return currentNonce;
    }

    // =================================================================
    // DEINE METHODEN - UNVERÄNDERT
    // =================================================================

    @Override
    public boolean createTransaction(BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data) {
        BigInteger transactionNonce = this.currentNonce;
        try {
            RawTransaction rawTransaction = RawTransaction.createEtherTransaction(transactionNonce, gasPrice, gasLimit, to, value);
            String signedTxHex = getWallet.signTransaction(rawTransaction);
            this.pendingTransactionJobs.add(new TransactionJob(transactionNonce, gasPrice, gasLimit, to, value, data, signedTxHex));
            currentNonce = currentNonce.add(BigInteger.ONE);
            return true;
        } catch (Exception e) {
            System.err.println("Fehler beim Erstellen oder Signieren der Transaktion: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean createTransaction(BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data) {
        try {
            RawTransaction rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, to, value);
            String signedTxHex = getWallet.signTransaction(rawTransaction);
            this.pendingTransactionJobs.add(new TransactionJob(nonce, gasPrice, gasLimit, to, value, data, signedTxHex));
            // HINWEIS: Bei manueller Nonce wird der Hauptzähler nicht erhöht. Das ist korrekt.
            return true;
        } catch (Exception e) {
            System.err.println("Fehler beim Erstellen oder Signieren der Transaktion: " + e.getMessage());
            return false;
        }
    }

    public List<String> getSignedTransactions() {
        List<String> hexStrings = new ArrayList<>();
        for (TransactionJob job : this.pendingTransactionJobs) {
            hexStrings.add(job.signedHex());
        }
        return hexStrings;
    }

    @Override
    public ArrayList<String> sendBatch() throws Exception {
        ArrayList<String> successfulHashes = new ArrayList<>();
        ArrayList<TransactionJob> jobsToProcess = new ArrayList<>(this.pendingTransactionJobs);
        this.pendingTransactionJobs.clear();

        while (!jobsToProcess.isEmpty()) {
            Iterator<TransactionJob> iterator = jobsToProcess.iterator();
            TransactionJob job = iterator.next();
            iterator.remove();

            try {
                String txHash = sendSignedTransaction(job.signedHex());
                successfulHashes.add(txHash);
                System.out.println("Transaktion erfolgreich gesendet: " + txHash + " (Nonce: " + job.nonce() + ")");
            } catch (RuntimeException e) {
                if (isNonceError(e)) {
                    System.err.println("Nonce-Fehler bei Nonce " + job.nonce() + " erkannt. Starte Korrekturprozess...");

                    ArrayList<TransactionJob> remainingJobs = new ArrayList<>();
                    remainingJobs.add(job);
                    iterator.forEachRemaining(remainingJobs::add);

                    jobsToProcess = correctAndRecreateJobs(remainingJobs, job.nonce());

                    System.out.println("Korrektur abgeschlossen. Setze Sendevorgang fort mit " + jobsToProcess.size() + " korrigierten Transaktionen...");
                } else {
                    throw new Exception("Nicht behebbarer Fehler im Batch bei Nonce " + job.nonce() + ": " + e.getMessage(), e);
                }
            }
        }
        return successfulHashes;
    }

    // =================================================================
    // BATCH SEND LOGIK - REFAKTORIERT UND ISOLIERT
    // =================================================================

    private boolean isNonceError(RuntimeException e) {
        String message = e.getMessage();
        return message != null && (message.contains("nonce too low") || message.contains("already known") || message.contains("invalid nonce"));
    }

    /**
     *Stellt sicher, dass die neue Nonce garantiert höher ist als die fehlgeschlagene.
     */
    private ArrayList<TransactionJob> correctAndRecreateJobs(List<TransactionJob> oldJobs, BigInteger failedNonce) throws Exception {
        resyncNonce();


        // Wenn die synchronisierte Nonce nicht größer ist als die, die gerade fehlgeschlagen ist,
        // erhöhen wir sie manuell. Das erzwingt den Fortschritt.
        if (this.currentNonce.compareTo(failedNonce) <= 0) {
            System.out.println("Erzwinge Nonce-Inkrementierung von " + this.currentNonce + " auf " + failedNonce.add(BigInteger.ONE));
            this.currentNonce = failedNonce.add(BigInteger.ONE);
        }

        ArrayList<TransactionJob> correctedJobs = new ArrayList<>();
        BigInteger nonceForCorrection = this.currentNonce;

        for (TransactionJob oldJob : oldJobs) {
            System.out.println("Erstelle Transaktion neu für Empfänger: " + oldJob.to() + " mit neuer Nonce: " + nonceForCorrection);

            RawTransaction newRawTx = RawTransaction.createEtherTransaction(nonceForCorrection, oldJob.gasPrice(), oldJob.gasLimit(), oldJob.to(), oldJob.value());
            String newSignedHex = getWallet.signTransaction(newRawTx);

            correctedJobs.add(new TransactionJob(nonceForCorrection, oldJob.gasPrice(), oldJob.gasLimit(), oldJob.to(), oldJob.value(), oldJob.data(), newSignedHex));

            nonceForCorrection = nonceForCorrection.add(BigInteger.ONE);
        }

        this.currentNonce = nonceForCorrection;
        return correctedJobs;
    }

    public List<TransactionJob> getPendingTransactionJobs() {
        return new ArrayList<>(this.pendingTransactionJobs);
    }

    // =================================================================
    // RESTLICHE METHODEN
    // =================================================================

    public void resyncNonce() throws IOException, InterruptedException, ExecutionException {
        this.currentNonce = web3j.ethGetTransactionCount(this.walletAddress, DefaultBlockParameterName.LATEST).send().getTransactionCount();
        System.out.println("Nonce synchronisiert für " + this.walletAddress + ". Neue Nonce: " + this.currentNonce);
    }

    public String sendSignedTransaction(String signedTransactionData) throws Exception {
        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(signedTransactionData).send();
        if (ethSendTransaction.hasError()) {
            throw new RuntimeException("Fehler beim Senden der Transaktion: " + ethSendTransaction.getError().getMessage());
        }
        return ethSendTransaction.getTransactionHash();
    }

    public boolean saveAndClearTransactionsToJson(File directory, String filename) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(this.pendingTransactionJobs);
        File finalFile = new File(directory, filename);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        try (FileWriter writer = new FileWriter(finalFile)) {
            writer.write(json);
        }
        this.pendingTransactionJobs.clear();
        System.out.println("Transaktions-Jobs erfolgreich in JSON gespeichert unter: " + finalFile.getAbsolutePath());
        return true;
    }

    public BigInteger fetchCurrentGasPrice() throws IOException {
        return web3j.ethGasPrice().send().getGasPrice();
    }

    public boolean loadTransactionsFromJsonAndDelete(String filePath) throws IOException {
        File sourceFile = new File(filePath);
        if (!sourceFile.exists()) {
            System.out.println("Datei nicht gefunden: " + filePath);
            return false;
        }
        Gson gson = new Gson();
        Type transactionJobListType = new TypeToken<ArrayList<TransactionJob>>() {
        }.getType();
        try (Reader reader = new FileReader(sourceFile)) {
            ArrayList<TransactionJob> loadedJobs = gson.fromJson(reader, transactionJobListType);
            if (loadedJobs != null && !loadedJobs.isEmpty()) {
                this.pendingTransactionJobs.clear();
                this.pendingTransactionJobs.addAll(loadedJobs);
                System.out.println(loadedJobs.size() + " Transaktions-Jobs erfolgreich aus " + filePath + " geladen.");
            } else {
                System.out.println("Keine Transaktions-Jobs in " + filePath + " gefunden oder Datei ist leer.");
            }
        }
        try {
            Files.delete(Paths.get(filePath));
            System.out.println("JSON-Datei erfolgreich gelöscht: " + filePath);
            return true;
        } catch (IOException e) {
            System.err.println("Fehler beim Löschen der Datei: " + filePath);
            throw e;
        }
    }

    @Override
    public boolean isOnline() {
        try {
            web3j.ethBlockNumber().send();
            return true;
        } catch (Exception e) {
            System.err.println("Netzwerkverbindungsproblem (isOnline Check): " + e.getMessage());
            return false;
        }
    }

    public Web3j getWeb3j() {
        return web3j;
    }

    public record TransactionJob(
            BigInteger nonce,
            BigInteger gasPrice,
            BigInteger gasLimit,
            String to,
            BigInteger value,
            String data,
            String signedHex
    ) {
    }

}