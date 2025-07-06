package demoprasenatationslayer;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import offlineTXCreator.OfflineTXCreator;
import offlineWallet.BalanceObserver;
import offlineWallet.OfflineWallet;
import offlineWallet.keystorefile.GenerateKeystorefile;
import offlineWallet.keystorefile.IKeystoreReader;
import offlineWallet.keystorefile.KeystoreGenerator;
import offlineWallet.keystorefile.Web3jKeystoreReader;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class WalletViewController implements BalanceObserver {

    // FXML-Felder aus allen Tabs
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
    @FXML
    private TableView<OfflineTXCreator.TransactionJob> transactionTableView;
    @FXML
    private Button sendBatchButton;
    @FXML
    private Button saveTxButton;
    @FXML
    private Button loadTxButton;
    @FXML
    private Button uploadButton;

    // Eigene Logik
    private Web3j web3j;
    private Optional<OfflineWallet> offlineWallet1Optional;
    private Optional<OfflineWallet> offlineWallet2Optional;
    private OfflineTXCreator txCreator1;
    private OfflineTXCreator txCreator2;
    private OfflineTXCreator activeTxCreatorForTable;

    @FXML
    private void initialize() {
        setupTransactionTable();

        try {
            web3j = Web3j.build(new HttpService("https://sepolia.drpc.org"));
            IKeystoreReader iKeystoreReader = new Web3jKeystoreReader();
            GenerateKeystorefile generateKeystorefile = new KeystoreGenerator();

            String wallet1FilePath = "C://BlockchainKey/UTC--2025-06-28T19-11-19.583110000Z--0295d8a45fab22cb581896a5996171dc9148a074.json";
            String wallet2FilePath = "C://BlockchainKey//Ichbincool.js";

            offlineWallet1Optional = OfflineWallet.loadWalletFromKeystore("1234", new File(wallet1FilePath), iKeystoreReader, generateKeystorefile);
            offlineWallet2Optional = OfflineWallet.loadWalletFromKeystore("6778371", new File(wallet2FilePath), iKeystoreReader, generateKeystorefile);

            if (offlineWallet1Optional.isPresent() && offlineWallet2Optional.isPresent()) {
                OfflineWallet wallet1 = offlineWallet1Optional.get();
                OfflineWallet wallet2 = offlineWallet2Optional.get();

                wallet1.addBalanceObserver(this);
                wallet2.addBalanceObserver(this);

                txCreator1 = new OfflineTXCreator(wallet1, web3j);
                txCreator2 = new OfflineTXCreator(wallet2, web3j);

                setSenderComboBox();
                recipientAddressField.setText("0x");
                startBalancePollingService();
            } else {
                statusLabel.setText("Fehler: Eine oder mehrere Wallet-Dateien konnten nicht geladen werden.");
                disableControls(true);
            }
        } catch (Exception e) {
            statusLabel.setText("Kritischer Initialisierungsfehler: " + e.getMessage());
            e.printStackTrace();
            disableControls(true);
        }
    }

    private void disableControls(boolean disable) {
        loadWalletsButton.setDisable(disable);
        refreshBalanceButton.setDisable(disable);
        createTransactionButton.setDisable(disable);
        loadTxButton.setDisable(disable);
        uploadButton.setDisable(disable);
        saveTxButton.setDisable(disable);
        sendBatchButton.setDisable(disable);
    }

    private void setupTransactionTable() {
        TableColumn<OfflineTXCreator.TransactionJob, BigInteger> nonceCol = new TableColumn<>("Nonce");
        nonceCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().nonce()));

        TableColumn<OfflineTXCreator.TransactionJob, String> toCol = new TableColumn<>("Empfänger");
        toCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().to()));
        toCol.setPrefWidth(250);

        TableColumn<OfflineTXCreator.TransactionJob, BigInteger> valueCol = new TableColumn<>("Wert (Wei)");
        valueCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().value()));
        valueCol.setPrefWidth(150);

        TableColumn<OfflineTXCreator.TransactionJob, String> hashCol = new TableColumn<>("Signierter Hash (gekürzt)");
        hashCol.setCellValueFactory(cellData -> {
            String fullHash = cellData.getValue().signedHex();
            String shortHash = fullHash.substring(0, 10) + "..." + fullHash.substring(fullHash.length() - 8);
            return new SimpleStringProperty(shortHash);
        });
        hashCol.setPrefWidth(200);

        transactionTableView.getColumns().setAll(nonceCol, toCol, valueCol, hashCol);
    }

    @Override
    public void updateBalance(BigInteger newBalance) {
        Platform.runLater(() -> {
            offlineWallet1Optional.ifPresent(wallet -> {
                try {
                    if (newBalance.equals(wallet.fetchBalance(web3j))) {
                        wallet1BalanceField.setText(newBalance.toString());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
            offlineWallet2Optional.ifPresent(wallet -> {
                try {
                    if (newBalance.equals(wallet.fetchBalance(web3j))) {
                        wallet2BalanceField.setText(newBalance.toString());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private void startBalancePollingService() {
        ScheduledService<Void> service = new ScheduledService<>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        offlineWallet1Optional.ifPresent(wallet -> {
                            try {
                                wallet.fetchBalance(web3j);
                            } catch (Exception e) {
                                System.err.println("Fehler beim Abrufen der Balance für Wallet 1: " + e.getMessage());
                            }
                        });
                        offlineWallet2Optional.ifPresent(wallet -> {
                            try {
                                wallet.fetchBalance(web3j);
                            } catch (Exception e) {
                                System.err.println("Fehler beim Abrufen der Balance für Wallet 2: " + e.getMessage());
                            }
                        });
                        return null;
                    }
                };
            }
        };
        service.setPeriod(Duration.seconds(20));
        service.start();
    }

    @FXML
    void createTransaction() {
        String selectedSenderString = senderComboBox.getSelectionModel().getSelectedItem();
        if (selectedSenderString == null) {
            statusLabel.setText("Bitte wählen Sie eine Absender-Wallet aus.");
            return;
        }

        OfflineTXCreator currentTxCreator = selectedSenderString.startsWith("Wallet 1") ? txCreator1 : txCreator2;

        try {
            BigInteger gasPrice = currentTxCreator.fetchCurrentGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(21000);
            String recipientAddress = recipientAddressField.getText();
            BigInteger value = new BigInteger(amountField.getText());

            if (recipientAddress.isEmpty() || !recipientAddress.startsWith("0x")) {
                statusLabel.setText("Ungültige Empfängeradresse.");
                return;
            }
            if (value.compareTo(BigInteger.ZERO) <= 0) {
                statusLabel.setText("Betrag muss größer als Null sein.");
                return;
            }

            boolean success = currentTxCreator.createTransaction(gasPrice, gasLimit, recipientAddress, value, null);
            if (success) {
                statusLabel.setText("Transaktion erfolgreich erstellt und signiert!");
                recipientAddressField.clear();
                recipientAddressField.setText("0x");
                amountField.clear();
                loadTransactionsFor(currentTxCreator);
            } else {
                statusLabel.setText("Fehler beim Erstellen der Transaktion.");
            }
        } catch (NumberFormatException e) {
            statusLabel.setText("Ungültiger Betrag. Bitte geben Sie eine Zahl ein.");
        } catch (IOException e) {
            statusLabel.setText("Fehler beim Abrufen des Gaspreises: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void loadWallets() throws IOException, ExecutionException, InterruptedException {
        System.out.println("Button 'Wallets laden' geklickt.");
        if (offlineWallet1Optional.isPresent() && offlineWallet2Optional.isPresent()) {
            wallet1AddressField.setText(offlineWallet1Optional.get().getHexadresse());
            wallet1BalanceField.setText(offlineWallet1Optional.get().fetchBalance(web3j).toString());
            wallet2AddressField.setText(offlineWallet2Optional.get().getHexadresse());
            wallet2BalanceField.setText(offlineWallet2Optional.get().fetchBalance(web3j).toString());
        }
    }

    @FXML
    void refreshBalances() throws IOException, ExecutionException, InterruptedException {
        System.out.println("Manuelle Aktualisierung getriggert.");
        if (offlineWallet1Optional.isPresent()) offlineWallet1Optional.get().fetchBalance(web3j);
        if (offlineWallet2Optional.isPresent()) offlineWallet2Optional.get().fetchBalance(web3j);
    }

    @FXML
    void loadTransactions() {
        System.out.println("Button 'Von Wallet Laden' geklickt.");
        showWalletSelectionDialog(this::loadTransactionsFromWallet);
    }

    @FXML
    void loadTransactionFromFile() {
        System.out.println("Button 'Von Datei Hochladen' geklickt.");
        showWalletSelectionDialog(this::loadTransactionsFromFile);
    }

    private void loadTransactionsFromWallet(OfflineTXCreator txCreator) {
        activeTxCreatorForTable = txCreator;
        List<OfflineTXCreator.TransactionJob> jobs = txCreator.getPendingTransactionJobs();
        transactionTableView.setItems(FXCollections.observableArrayList(jobs));
        statusLabel.setText(jobs.size() + " Transaktion(en) aus dem Speicher geladen.");
    }

    private void loadTransactionsFromFile(OfflineTXCreator txCreator) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Gespeicherte Transaktionen laden");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON-Dateien", "*.json"));
        File selectedFile = fileChooser.showOpenDialog(uploadButton.getScene().getWindow());

        if (selectedFile != null) {
            try {
                txCreator.loadTransactionsFromJsonAndDelete(selectedFile.getAbsolutePath());
                loadTransactionsFromWallet(txCreator);
                statusLabel.setText("Transaktionen aus " + selectedFile.getName() + " geladen.");
            } catch (IOException e) {
                statusLabel.setText("Fehler beim Laden der Datei: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            statusLabel.setText("Dateiauswahl abgebrochen.");
        }
    }

    private void showWalletSelectionDialog(java.util.function.Consumer<OfflineTXCreator> action) {
        List<String> choices = Arrays.asList("Wallet 1", "Wallet 2");
        ChoiceDialog<String> dialog = new ChoiceDialog<>("Wallet 1", choices);
        dialog.setTitle("Wallet-Auswahl");
        dialog.setHeaderText("Wähle eine Wallet aus");
        dialog.setContentText("Für welche Wallet soll die Aktion ausgeführt werden?:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(selectedWallet -> {
            OfflineTXCreator selectedTxCreator = selectedWallet.equals("Wallet 1") ? txCreator1 : txCreator2;
            action.accept(selectedTxCreator);
        });
    }

    private void loadTransactionsFor(OfflineTXCreator txCreator) {
        activeTxCreatorForTable = txCreator;
        if (txCreator != null) {
            List<OfflineTXCreator.TransactionJob> signedTransactions = txCreator.getPendingTransactionJobs();
            transactionTableView.setItems(FXCollections.observableArrayList(signedTransactions));
            statusLabel.setText(signedTransactions.size() + " Transaktion(en) geladen.");
        }
    }

    @FXML
    void sendTransactionBatch() {
        System.out.println("Button 'Alle Transaktionen senden' geklickt.");

        if (activeTxCreatorForTable == null) {
            statusLabel.setText("Fehler: Bitte zuerst Transaktionen für eine Wallet laden.");
            return;
        }

        if (activeTxCreatorForTable.getPendingTransactionJobs().isEmpty()) {
            statusLabel.setText("Keine ausstehenden Transaktionen zum Senden vorhanden.");
            return;
        }

        try {
            ArrayList<String> sentHashes = activeTxCreatorForTable.sendBatch();
            statusLabel.setText(sentHashes.size() + " Transaktion(en) erfolgreich gesendet!");
            transactionTableView.getItems().clear();
            refreshBalances();
        } catch (Exception e) {
            statusLabel.setText("Fehler beim Senden: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    void saveTransactions() {
        System.out.println("Button 'Als Datei Speichern' geklickt.");

        // Dialog zur Auswahl der Wallet anzeigen
        showWalletSelectionDialog(txCreator -> {
            if (txCreator.getPendingTransactionJobs().isEmpty()) {
                statusLabel.setText("Keine Transaktionen zum Speichern für diese Wallet vorhanden.");
                return;
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Transaktionen speichern unter...");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON-Dateien", "*.json"));
            fileChooser.setInitialFileName("signed_transactions.json");
            File file = fileChooser.showSaveDialog(saveTxButton.getScene().getWindow());

            if (file != null) {
                try {
                    // Die Methode in OfflineTXCreator speichert UND leert die Liste
                    txCreator.saveAndClearTransactionsToJson(file.getParentFile(), file.getName());


                    // Da die Liste im Creator jetzt leer ist, auch die Tabelle leeren
                    transactionTableView.getItems().clear();
                    activeTxCreatorForTable = null; // Es gibt keinen aktiven Creator mehr für die Tabelle

                } catch (IOException e) {
                    statusLabel.setText("Fehler beim Speichern der Datei: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                statusLabel.setText("Speichervorgang abgebrochen.");
            }
        });
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