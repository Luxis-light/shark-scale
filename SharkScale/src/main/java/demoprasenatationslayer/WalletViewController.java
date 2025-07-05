package demoprasenatationslayer;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import offlineTXCreator.OfflineTXCreator;
import offlineWallet.OfflineWallet;
import offlineWallet.keystorefile.GenerateKeystorefile;
import offlineWallet.keystorefile.IKeystoreReader;
import offlineWallet.keystorefile.KeystoreGenerator;
import offlineWallet.keystorefile.Web3jKeystoreReader;
import org.web3j.crypto.CipherException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class WalletViewController {

    // Tab: Wallet Übersicht
    @FXML
    private TextField wallet1AddressField;
    @FXML
    private TextField wallet1BalanceField;
    @FXML
    private TextField wallet2AddressField;
    @FXML
    private TextField wallet2BalanceField;
    @FXML
    private Button loadWalletsButton;
    @FXML
    private Button refreshBalanceButton;

    // Tab: Transaktion erstellen
    @FXML
    private ComboBox<String> senderComboBox;
    @FXML
    private TextField recipientAddressField;
    @FXML
    private TextField amountField;
    @FXML
    private Button createTransactionButton;
    @FXML
    private Label statusLabel;

    // Tab: Ausstehende Transaktionen
    @FXML
    private TableView<Object> transactionTableView; // Wir verwenden Object als Platzhalter
    @FXML
    private Button sendBatchButton;
    @FXML
    private Button saveTxButton;
    @FXML
    private Button loadTxButton;

    // eigene Logik
    private Web3j web3j = Web3j.build(new HttpService("https://sepolia.drpc.org"));
    private GenerateKeystorefile generateKeystorefile = new KeystoreGenerator();
    private IKeystoreReader iKeystoreReader = new Web3jKeystoreReader();
    private String wallet1FilePath = "/Users/kacper/Studium/04_Semester/Dezentralisierte Systeme/Offline Wallet/Wallets/UTC--2025-06-28T19-11-19.583110000Z--0295d8a45fab22cb581896a5996171dc9148a074.json";
    private String wallet2FilePath = "/Users/kacper/Studium/04_Semester/Dezentralisierte Systeme/Offline Wallet/Wallets/Ich bin cool.json";

    // Wallet Instanzen
    private Optional<OfflineWallet> offlineWallet1Optional;
    private Optional<OfflineWallet> offlineWallet2Optional;

    // TXCreator Instanzen für jede Wallet
    private OfflineTXCreator txCreator1;
    private OfflineTXCreator txCreator2;
    @FXML
    private void initialize() throws CipherException, IOException, ExecutionException, InterruptedException {
        // Diese Methode wird aufgerufen, nachdem die FXML-Datei geladen wurde.
        // Wallets aus den exportierten Dateien laden
        offlineWallet1Optional = OfflineWallet.loadWalletFromKeystore("1234", new File(wallet1FilePath), iKeystoreReader, generateKeystorefile);
        offlineWallet2Optional = OfflineWallet.loadWalletFromKeystore("6778371", new File(wallet2FilePath), iKeystoreReader, generateKeystorefile);

        txCreator1 = new OfflineTXCreator(offlineWallet1Optional.get(), web3j);
        txCreator2 = new OfflineTXCreator(offlineWallet2Optional.get(), web3j);

        // Initialisierung im "Transaktion Erstellen Tab"
        setSenderComboBox();
        recipientAddressField.setText("0x");
    }

    @FXML
    void loadWallets() throws IOException, ExecutionException, InterruptedException {
        System.out.println("Button 'Wallets laden' geklickt.");
        wallet1AddressField.setText(offlineWallet1Optional.get().getHexadresse());
        wallet1BalanceField.setText(offlineWallet1Optional.get().fetchBalance(web3j).toString());
        wallet2AddressField.setText(offlineWallet2Optional.get().getHexadresse());
        wallet2BalanceField.setText(offlineWallet2Optional.get().fetchBalance(web3j).toString());
    }

    @FXML
    void refreshBalances() throws IOException, ExecutionException, InterruptedException {
        System.out.println("Button 'Guthaben aktualisieren' geklickt.");
        wallet1BalanceField.setText(offlineWallet1Optional.get().fetchBalance(web3j).toString());
        wallet2BalanceField.setText(offlineWallet2Optional.get().fetchBalance(web3j).toString());
    }

    @FXML
    void createTransaction() {
        System.out.println("Button 'Transaktion erstellen' geklickt.");
        // TODO: Logik zum Erstellen und Signieren einer Transaktion.
        String selectedSenderString = senderComboBox.getSelectionModel().getSelectedItem();

        if (selectedSenderString == null) {
            statusLabel.setText("Bitte wählen Sie eine Absender-Wallet aus.");
            return;
        }

        OfflineTXCreator currentTxCreator;
        if (selectedSenderString.startsWith("Wallet 1") && txCreator1 != null) {
            currentTxCreator = txCreator1;
        } else if (selectedSenderString.startsWith("Wallet 2") && txCreator2 != null) {
            currentTxCreator = txCreator2;
        } else {
            statusLabel.setText("Ausgewählte Wallet ist nicht geladen oder ungültig.");
            return;
        }

        try {
            BigInteger gasPrice = currentTxCreator.fetchCurrentGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(21000); // Standard Gas Limit für einfache Ether-Transaktionen
            String recipientAddress = recipientAddressField.getText();
            BigInteger value = new BigInteger(amountField.getText()); // Betrag in Wei

            if (recipientAddress.isEmpty() || !recipientAddress.startsWith("0x")) {
                statusLabel.setText("Ungültige Empfängeradresse.");
                return;
            }

            if (value.compareTo(BigInteger.ZERO) <= 0) {
                statusLabel.setText("Betrag muss größer als Null sein.");
                return;
            }

            // Nonce wird automatisch vom OfflineTXCreator verwaltet
            boolean success = currentTxCreator.createTransaction(gasPrice, gasLimit, recipientAddress, value, null);

            if (success) {
                statusLabel.setText("Transaktion erfolgreich erstellt und signiert!");
                // Optional: Felder leeren
                recipientAddressField.clear();
                amountField.clear();
            } else {
                statusLabel.setText("Fehler beim Erstellen der Transaktion.");
            }

        } catch (NumberFormatException e) {
            statusLabel.setText("Ungültiger Betrag. Bitte geben Sie eine Zahl ein.");
        } catch (IOException | InterruptedException e) {
            statusLabel.setText("Fehler beim Abrufen des Gaspreises oder der Nonce: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            statusLabel.setText("Ein unerwarteter Fehler ist aufgetreten: " + e.getMessage());
            e.printStackTrace();
        }
    }



    @FXML
    void sendTransactionBatch() {
        System.out.println("Button 'Alle Transaktionen senden' geklickt.");
        // TODO: Logik zum Senden des Batches.
    }

    @FXML
    void saveTransactions() {
        System.out.println("Button 'Transaktionen speichern' geklickt.");
        // TODO: Logik zum Speichern der TX-Liste.
    }

    @FXML
    void loadTransactions() {
        System.out.println("Button 'Transaktionen laden' geklickt.");
        // TODO: Logik zum Laden der TX-Liste.
    }

    private void setSenderComboBox() {
        if (offlineWallet1Optional.isPresent() && offlineWallet2Optional.isPresent()) {
            senderComboBox.getItems().add("Wallet 1: " + offlineWallet1Optional.get().getHexadresse());
            senderComboBox.getItems().add("Wallet 2: " + offlineWallet2Optional.get().getHexadresse());
        } else {
            System.out.println("Wallets konnten nicht geladen werden für die Combo Box.");
        }
    }
}