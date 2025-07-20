package offlineTXCreator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import offlineWallet.GetWallet;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;

import java.io.*;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Kernklasse zur Erstellung, Signierung und Verwaltung von Offline-Transaktionen.
 * <p>
 * Diese Klasse kapselt die Logik, um Ethereum-Transaktionen vorzubereiten, ohne dass
 * eine ständige Netzwerkverbindung erforderlich ist. Sie verwaltet eine interne Nonce,
 * signiert Transaktionen EIP-155-konform und kann diese gebündelt (als "Batch") versenden,
 * sobald eine Netzwerkverbindung besteht. Sie bietet zudem Mechanismen zur Fehlerbehandlung
 * bei Nonce-Konflikten und zur Persistierung von Transaktions-Jobs in JSON-Dateien.
 *
 * @author Luca
 * @version 1.0
 */
public class OfflineTXCreator implements INetworkConnection, ITXCreator {

    private final ArrayList<TransactionJob> pendingTransactionJobs;
    private final GetWallet getWallet;
    private BigInteger currentNonce;
    private final Web3j web3j;
    private final String walletAddress;
    private final long chainId;

    /**
     * Hauptkonstruktor zur Initialisierung des TX-Creators.
     * Ruft die Chain-ID vom Netzwerk ab und synchronisiert die Nonce, sofern keine initiale Nonce angegeben ist.
     *
     * @param getWallet    Der Wallet-Provider, der Zugriff auf die Credentials zum Signieren bietet.
     * @param web3j        Die Web3j-Instanz für die Blockchain-Kommunikation.
     * @param initialNonce Eine optionale, manuell gesetzte Start-Nonce. Wenn null, wird die Nonce vom Netzwerk abgerufen.
     * @throws IOException Wenn die Kommunikation mit dem Ethereum-Knoten fehlschlägt.
     */
    public OfflineTXCreator(GetWallet getWallet, Web3j web3j, BigInteger initialNonce) throws IOException {
        if (getWallet == null) throw new IllegalArgumentException("Wallet-Provider darf nicht null sein");
        if (web3j == null) throw new IllegalArgumentException("Web3j darf nicht null sein");

        this.getWallet = getWallet;
        this.web3j = web3j;
        this.chainId = web3j.ethChainId().send().getChainId().longValue();
        this.pendingTransactionJobs = new ArrayList<>();
        this.walletAddress = getWallet.getCredentials().getAddress();

        if (initialNonce != null) {
            if (initialNonce.compareTo(BigInteger.ZERO) < 0) {
                throw new IllegalArgumentException("Anfängliche Nonce darf nicht negativ sein");
            }
            this.currentNonce = initialNonce;
        } else {
            try {
                this.resyncNonce();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Nonce konnte im Konstruktor nicht synchronisiert werden", e);
            }
        }
    }

    /**
     * Vereinfachter Konstruktor, der die Nonce automatisch vom Netzwerk abruft.
     *
     * @param getWallet Der Wallet-Provider, der Zugriff auf die Credentials bietet.
     * @param web3j     Die Web3j-Instanz für die Blockchain-Kommunikation.
     * @throws IOException, InterruptedException, ExecutionException bei Fehlern in der Netzwerkkommunikation.
     */
    public OfflineTXCreator(GetWallet getWallet, Web3j web3j) throws IOException, InterruptedException, ExecutionException {
        this(getWallet, web3j, null);
    }

    /**
     * Gibt die aktuell vom Creator intern verwaltete Nonce zurück.
     * Diese Nonce wird für die nächste automatisch erstellte Transaktion verwendet.
     *
     * @return Die aktuelle interne Nonce als BigInteger.
     */
    public BigInteger getCurrentNonce() {
        return currentNonce;
    }

    @Override
    public boolean createTransaction(BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data) {
        BigInteger transactionNonce = this.currentNonce;
        try {
            RawTransaction rawTransaction = RawTransaction.createEtherTransaction(transactionNonce, gasPrice, gasLimit, to, value);
            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, this.chainId, getWallet.getCredentials());
            String signedTxHex = Numeric.toHexString(signedMessage);
            this.pendingTransactionJobs.add(new TransactionJob(this.walletAddress, transactionNonce, gasPrice, gasLimit, to, value, data, signedTxHex));
            currentNonce = currentNonce.add(BigInteger.ONE);
            return true;
        } catch (Exception e) {
            System.err.println("Fehler beim Erstellen oder Signieren der Transaktion: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean createTransaction(BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String to, BigInteger value, String data) {
        try {
            RawTransaction rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, to, value);
            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, this.chainId, getWallet.getCredentials());
            String signedTxHex = Numeric.toHexString(signedMessage);
            this.pendingTransactionJobs.add(new TransactionJob(this.walletAddress, nonce, gasPrice, gasLimit, to, value, data, signedTxHex));
            return true;
        } catch (Exception e) {
            System.err.println("Fehler beim Erstellen oder Signieren der Transaktion: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sammelt die Hex-Strings aller signierten, aber noch nicht gesendeten Transaktionen.
     *
     * @return Eine Liste von Hex-Strings der signierten Transaktionen.
     */
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

        while (!jobsToProcess.isEmpty()) {
            Iterator<TransactionJob> iterator = jobsToProcess.iterator();
            TransactionJob job = iterator.next();
            // WICHTIG: Den Job hier noch NICHT aus der Liste entfernen

            try {
                String txHash = sendSignedTransaction(job.signedHex());
                if (txHash != null && !txHash.isEmpty()) {
                    successfulHashes.add(txHash);
                } else {
                    throw new IOException("Transaction sent but node returned null/empty hash for nonce " + job.nonce());
                }
                // Job erst nach Erfolg entfernen
                iterator.remove();

            } catch (RuntimeException e) {
                if (isNonceError(e)) {
                    System.err.println("Nonce-Fehler bei Nonce " + job.nonce() + " erkannt. Starte Korrekturprozess...");

                    // --- HIER IST DIE KORREKTUR ---
                    // 1. Sammle alle verbleibenden Jobs (den aktuellen und die folgenden)
                    ArrayList<TransactionJob> remainingJobs = new ArrayList<>();
                    remainingJobs.add(job); // Füge den fehlgeschlagenen Job hinzu
                    iterator.forEachRemaining(remainingJobs::add); // Füge alle nachfolgenden Jobs hinzu

                    // 2. Rufe die Korrekturmethode auf und ersetze den aktuellen Stapel
                    jobsToProcess = correctAndRecreateJobs(remainingJobs, job.nonce());

                    System.out.println("Korrektur abgeschlossen. Setze Sendevorgang mit " + jobsToProcess.size() + " korrigierten Transaktionen fort...");
                    // Der while-Loop wird nun mit den neuen, korrigierten Jobs fortgesetzt

                } else {
                    // Bei einem anderen Runtime-Fehler abbrechen
                    throw new Exception("Nicht behebbarer Fehler im Batch bei Nonce " + job.nonce() + ": " + e.getMessage(), e);
                }
            }
        }
        return successfulHashes;
    }

    /**
     * Verarbeitet einen übergebenen Stapel von Jobs mit der bestehenden sendBatch-Logik.
     * Dies ist die bevorzugte Methode für externe Aufrufe wie von einem UI-Controller.
     *
     * @param jobs Die Liste der Transaktions-Jobs, die gesendet werden sollen.
     * @return Eine Liste der erfolgreichen Transaktions-Hashes.
     * @throws Exception wenn beim Senden ein Fehler auftritt.
     */
    public ArrayList<String> sendJobs(List<TransactionJob> jobs) throws Exception {
        // Setzt temporär die interne Liste auf die zu verarbeitenden Jobs
        this.pendingTransactionJobs.clear();
        this.pendingTransactionJobs.addAll(jobs);
        // Ruft die bestehende sendBatch-Logik auf, die auf der internen Liste arbeitet
        return this.sendBatch();
    }

    public void clearPendingJobs() {
        this.pendingTransactionJobs.clear();
    }

    private ArrayList<TransactionJob> correctAndRecreateJobs(List<TransactionJob> oldJobs, BigInteger failedNonce) throws Exception {
        resyncNonce();

        if (this.currentNonce.compareTo(failedNonce) <= 0) {
            System.out.println("Erzwinge Nonce-Inkrementierung von " + this.currentNonce + " auf " + failedNonce.add(BigInteger.ONE));
            this.currentNonce = failedNonce.add(BigInteger.ONE);
        }

        ArrayList<TransactionJob> correctedJobs = new ArrayList<>();
        BigInteger nonceForCorrection = this.currentNonce;

        for (TransactionJob oldJob : oldJobs) {
            System.out.println("Erstelle Transaktion neu für Empfänger: " + oldJob.to() + " mit neuer Nonce: " + nonceForCorrection);

            RawTransaction newRawTx = RawTransaction.createEtherTransaction(nonceForCorrection, oldJob.gasPrice(), oldJob.gasLimit(), oldJob.to(), oldJob.value());
            byte[] signedMessage = TransactionEncoder.signMessage(newRawTx, this.chainId, getWallet.getCredentials());
            String newSignedHex = Numeric.toHexString(signedMessage);

            correctedJobs.add(new TransactionJob(this.walletAddress, nonceForCorrection, oldJob.gasPrice(), oldJob.gasLimit(), oldJob.to(), oldJob.value(), oldJob.data(), newSignedHex));
            nonceForCorrection = nonceForCorrection.add(BigInteger.ONE);
        }

        this.currentNonce = nonceForCorrection;
        return correctedJobs;
    }

    private boolean isNonceError(RuntimeException e) {
        String message = e.getMessage();
        return message != null && (message.contains("nonce too low") || message.contains("already known") || message.contains("invalid nonce"));
    }

    /**
     * Gibt eine Kopie der Liste der aktuell ausstehenden Transaktions-Jobs zurück.
     * Jeder Job enthält alle Details der Transaktion.
     *
     * @return Eine neue Liste mit den anstehenden {@link TransactionJob}s.
     */
    public List<TransactionJob> getPendingTransactionJobs() {
        return new ArrayList<>(this.pendingTransactionJobs);
    }

    /**
     * Synchronisiert die interne Nonce mit dem aktuellen Transaktionszähler der Wallet-Adresse vom Netzwerk.
     * Dies ist nützlich, um die Nonce nach externen Transaktionen oder Fehlern zu korrigieren.
     *
     * @throws IOException, InterruptedException, ExecutionException bei Fehlern in der Netzwerkkommunikation.
     */
    public void resyncNonce() throws IOException, InterruptedException, ExecutionException {
        this.currentNonce = web3j.ethGetTransactionCount(this.walletAddress, DefaultBlockParameterName.LATEST).send().getTransactionCount();
        System.out.println("Nonce synchronisiert für " + this.walletAddress + ". Neue Nonce: " + this.currentNonce);
    }

    /**
     * Sendet eine einzelne, bereits signierte Transaktion an das Ethereum-Netzwerk.
     *
     * @param signedTransactionData Die signierte Transaktion als Hex-String (beginnend mit "0x").
     * @return Der Transaktions-Hash bei erfolgreichem Versand.
     * @throws Exception Wenn der Knoten einen Fehler zurückgibt.
     */
    public String sendSignedTransaction(String signedTransactionData) throws Exception {
        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(signedTransactionData).send();
        if (ethSendTransaction.hasError()) {
            throw new RuntimeException("Fehler beim Senden der Transaktion: " + ethSendTransaction.getError().getMessage());
        }
        return ethSendTransaction.getTransactionHash();
    }


    /**
     * Speichert alle ausstehenden Transaktions-Jobs in einer JSON-Datei und leert anschließend die interne Liste.
     *
     * @param directory Das Verzeichnis, in dem die Datei gespeichert werden soll.
     * @param filename  Der Name der zu erstellenden JSON-Datei.
     * @return true, wenn das Speichern erfolgreich war.
     * @throws IOException Wenn ein Fehler beim Schreiben der Datei auftritt.
     */
    @Override
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

    /**
     * Ruft den aktuellen, vom Netzwerk empfohlenen Gaspreis ab.
     *
     * @return Der aktuelle Gaspreis in Wei.
     * @throws IOException Wenn ein Fehler bei der Netzwerkanfrage auftritt.
     */
    public BigInteger fetchCurrentGasPrice() throws IOException {
        return web3j.ethGasPrice().send().getGasPrice();
    }

    /**
     * Lädt Transaktions-Jobs aus einer JSON-Datei in die interne Liste und löscht die Quelldatei.
     * Bereits vorhandene Jobs in der Liste werden zuvor gelöscht.
     *
     * @param filePath Der vollständige Pfad zur JSON-Datei.
     * @return true, wenn das Laden und Löschen erfolgreich war.
     * @throws IOException Wenn ein Fehler beim Lesen oder Löschen der Datei auftritt.
     */
    @Override
    public boolean loadTransactionsFromJsonAndDelete(String filePath) throws IOException {
        File sourceFile = new File(filePath);
        if (!sourceFile.exists()) {
            System.out.println("Datei nicht gefunden: " + filePath);
            return false;
        }

        Gson gson = new Gson();
        // Stellen Sie sicher, dass Ihr TransactionJob-Record das Feld "ownerAddress" hat
        Type transactionJobListType = new TypeToken<ArrayList<TransactionJob>>() {
        }.getType();

        try (Reader reader = new FileReader(sourceFile)) {
            ArrayList<TransactionJob> loadedJobs = gson.fromJson(reader, transactionJobListType);
            if (loadedJobs != null && !loadedJobs.isEmpty()) {
                // Bestehende Jobs löschen, um nur die neuen zu laden
                this.pendingTransactionJobs.clear();

                // --- KORREKTUR: Filtern der Jobs nach Besitzer ---
                for (TransactionJob loadedJob : loadedJobs) {
                    // Strikte Prüfung: Gehört dieser Job zur aktuellen Wallet? (Groß-/Kleinschreibung ignorieren)
                    if (this.walletAddress.equalsIgnoreCase(loadedJob.ownerAddress())) {
                        this.pendingTransactionJobs.add(loadedJob);
                    } else {
                        // Protokollieren, dass ein fremder Job ignoriert wird
                        System.out.println("Info: Überspringe Transaktion mit Nonce "
                                + loadedJob.nonce()
                                + ", da sie zu einer anderen Wallet gehört ("
                                + loadedJob.ownerAddress() + ").");
                    }
                }
                System.out.println(this.pendingTransactionJobs.size() + " passende Transaktions-Jobs erfolgreich aus " + filePath + " geladen.");

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

    /**
     * Gibt die für diesen Creator verwendete Web3j-Instanz zurück.
     *
     * @return Die aktive {@link Web3j}-Instanz.
     */
    public Web3j getWeb3j() {
        return web3j;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    /**
     * Ein Record, der alle relevanten Daten für eine signierte Transaktion unveränderlich speichert.
     */
    public record TransactionJob(
            String ownerAddress,
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