# Offline Wallet & Transaction Signer

Dies ist ein Prototyp einer **Offline Wallet** mit der Fähigkeit, Transaktionen sicher zu signieren und für den späteren Versand vorzubereiten. Das System ist darauf ausgelegt, die Erstellung und Speicherung signierter Transaktionen offline zu ermöglichen, um ein Höchstmaß an Sicherheit für private Schlüssel zu gewährleisten. Der Versand der Transaktionen erfolgt erst, wenn ein externer Trigger eine Netzwerkverbindung herstellt und die Blockchain-Interaktion ermöglicht.

## Kernidee

Die Hauptidee ist es, einen Air-Gap zwischen dem privaten Schlüssel und der Online-Welt zu schaffen. Die Offline Wallet speichert Ihre privaten/öffentlichen Schlüssel und die digital signierten Transaktionen. Diese signierten Transaktionen werden lokal vorgehalten und erst bei Bedarf – durch eine explizite externe Anweisung und bei Verfügbarkeit einer Netzwerkverbindung – an die Blockchain übermittelt.

## Funktionen

* **PKI Key Importieren**: Ermöglicht den sicheren Import von PKI-Schlüsseln in die Offline Wallet.
* **TX Erzeugen**: Erstellung und Signierung von Transaktionen (TX) offline.
* **TX Auflisten**: Anzeige aller in der Offline Wallet gespeicherten, signierten Transaktionen.
* **TX Senden**: Übermittlung der gespeicherten Transaktionen an die Blockchain, *sobald* eine Netzwerkverbindung besteht und ein externer Trigger den Versand auslöst.

## Architekturübersicht

Das System besteht aus drei Hauptkomponenten:

* **Offline Wallet**: Verwaltet die privaten/öffentlichen Schlüssel und speichert die signierten Transaktionen sicher offline.
* **Offline TX Creator**: Eine Komponente, die es dem Benutzer ermöglicht, Transaktionen zu erstellen und mithilfe der Offline Wallet zu signieren. Sie fungiert als Schnittstelle zwischen dem Benutzer und der Offline Wallet.
* **Blockchain**: Repräsentiert das Zielnetzwerk, an das die signierten Transaktionen übermittelt werden. Die Kommunikation erfolgt nur, wenn der "issued if online"-Zustand erreicht ist.
  Das detaillierte Komponenten-Diagramm finden Sie hier: [Komponenten Diagramm](docs/uml/Komponenten%20Diagramm.png)
## Geplante Erweiterungen (Spätere Phasen)
Security Aspekte: Detaillierte Betrachtung und Implementierung von Sicherheitsmechanismen für den privaten Schlüssel und die Transaktionsverarbeitung.
