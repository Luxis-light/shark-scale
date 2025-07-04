package demoprasenatationslayer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class uidemo extends Application {

    public static void main(String[] args) {
        // Startet die JavaFX-Anwendung
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // Lädt die Benutzeroberfläche aus der FXML-Datei
            Parent root = FXMLLoader.load(getClass().getResource("/WalletView.fxml"));

            // Erstellt eine neue Szene mit der geladenen Oberfläche
            Scene scene = new Scene(root);

            // Setzt den Titel des Hauptfensters
            primaryStage.setTitle("Shark-Scale Offline Wallet");
            primaryStage.setScene(scene);

            // Verhindert, dass die Fenstergröße geändert werden kann
            primaryStage.setResizable(false);

            // Zeigt das Fenster an
            primaryStage.show();

        } catch (IOException e) {
            // Gibt einen Fehler aus, wenn die FXML-Datei nicht geladen werden kann
            e.printStackTrace();
        }
    }
}