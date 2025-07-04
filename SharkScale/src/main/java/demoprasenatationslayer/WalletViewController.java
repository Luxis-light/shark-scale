package demoprasenatationslayer;

import javafx.fxml.FXML;
import javafx.scene.control.*;

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

    // Platzhalter für deine Logik

    @FXML
    private void initialize() {
        // Diese Methode wird aufgerufen, nachdem die FXML-Datei geladen wurde.
        // Perfekt für Initialisierungen.
    }

    @FXML
    void loadWallets() {
        System.out.println("Button 'Wallets laden' geklickt.");
        // TODO: Hier kommt die Logik zum Laden der Wallets hin.
    }

    @FXML
    void refreshBalances() {
        System.out.println("Button 'Guthaben aktualisieren' geklickt.");
        // TODO: Logik zum Aktualisieren der Guthabenanzeige.
    }

    @FXML
    void createTransaction() {
        System.out.println("Button 'Transaktion erstellen' geklickt.");
        // TODO: Logik zum Erstellen und Signieren einer Transaktion.
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
}