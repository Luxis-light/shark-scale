# 🦈 shark-scale: Secure Offline Wallet System

![UML Component Diagram](docs/uml/komponentendiagramm.png) ## 🌟 1. Projektübersicht

`shark-scale` ist ein speziell entwickeltes Offline-Wallet-System, das auf maximale Sicherheit durch strikte Trennung von Online- und Offline-Prozessen ausgelegt ist. Sein primäres Ziel ist die hochsichere Generierung, Speicherung und Signierung von Kryptowährungs-Transaktionen in einer isolierten Umgebung, ohne Private Keys jemals dem Internet auszusetzen.

Dieses Projekt dient als Proof-of-Concept / Prototyp / [Wähle zutreffendes: Implementierung] im Rahmen des Kurses "[Name des Kurses, z.B. Secure Software Engineering]" an der HTW Berlin. Es wird von einem [Anzahl des Teams, z.B. dreiköpfigen] Team entwickelt.

## ✨ 2. Technische Architektur & Kernkomponenten

Die Systemarchitektur von `shark-scale` ist modular und komponentenorientiert aufgebaut, um eine klare Trennung der Verantwortlichkeiten (Separation of Concerns) und eine robuste Sicherheitsbasis zu gewährleisten. Die Kernfunktionalität konzentriert sich auf die **Offline-Transaktionssignierung**. Interaktionen mit der Blockchain sind auf das absolute Minimum beschränkt und finden nur über definierte, kontrollierte Schnittstellen statt.

**Schlüsselkomponenten:**

* **`OfflineWalletCore`**: Die zentrale Geschäftslogik des Wallets, die High-Level-Operationen wie Adressableitung und Transaktionsvorbereitung orchestriert.
* **`KeyManagementModule`**: Verantwortlich für die hochsichere Generierung, das Laden, Speichern und die Isolation privater Schlüssel. Implementiert [Beschreibe Schlüsselverwaltung, z.B. HD-Wallet-Standards (BIP32/BIP39), Speichermechanismen].
* **`TransactionSignerModule`**: Eine dedizierte, isolierte Komponente, die den kryptographischen Signaturprozess für Transaktionen ohne Netzwerkzugriff durchführt. [Erwähne hier verwendete Krypto-Bibliotheken oder Protokolle, z.B. ECDSA, SHA-256].
* **`DataPersistenceModule`**: Verwaltet die Speicherung nicht-sensibler Wallet-Daten und Konfigurationen (z.B. Adresslisten, Transaktions-IDs, Settings). [Beschreibe den Speichermechanismus, z.B. Dateisystem, verschlüsselte lokale Datenbank].
* **`IOManager`**: Die Schnittstelle für den sicheren Import von unsignierten Transaktionsanfragen und den Export von signierten Transaktionen. Dies kann über [z.B. QR-Codes, USB-Laufwerke, dedizierte Dateiformate] erfolgen.
* **`INetwork` Interface**: Ein definiertes Interface, das die Abstraktion des Netzwerkzugriffs ermöglicht. Implementierungen dieses Interfaces sind für den Versand von signierten Transaktionen an das Blockchain-Netzwerk oder den Empfang von Blockchain-Daten (z.B. UTXOs) zuständig – jedoch **ausschließlich außerhalb der kritischen Schlüssel- und Signierungsbereiche**.
* **`ITXCreator` Interface**: Definiert den Vertrag für die Erstellung von rohen, unsignierten Transaktionsstrukturen. Dies ermöglicht eine flexible Anbindung an verschiedene Transaktionstypen oder -formate.
* **`[Weitere Komponenten]`**: [Beschreibe hier weitere spezialisierte Module, z.B. für Kryptographie-Primitive, Fehlerbehandlung, Logging etc.]

### 2.1. Komponentendiagramm

Eine visuelle Darstellung der Systemarchitektur und der Interaktionen zwischen diesen Komponenten ist im UML-Komponentendiagramm verfügbar:
* `[Link zum UML-Diagramm, z.B. docs/uml/komponentendiagramm.png]`

## 🛠️ 3. Technologie-Stack & Tools

* **Programmiersprache:** Java [Wähle Version, z.B. 17, 21]
* **Build-Automatisierung:** Apache Maven [Wähle Version, z.B. 3.x.x]
* **Abhängigkeiten:**
    * [Liste hier wichtige Core-Bibliotheken auf, z.B. `org.bouncycastle` für Krypto, `com.google.zxing` für QR, `org.json` für JSON-Parsing, etc.]
    * [Erwähne Test-Frameworks, z.B. `org.junit.jupiter` für Unit-Tests]
* **Entwicklungsumgebung (IDE):** IntelliJ IDEA (Empfohlen)
* **Diagramm-Tool:** [z.B. PlantUML (als Code), Draw.io, Lucidchart]

## 🚀 4. Erste Schritte (Getting Started)

Diese Anleitung hilft dir, das Projekt lokal aufzusetzen und zu starten.

### 4.1. Voraussetzungen

* Java Development Kit (JDK) [Versionsnummer, z.B. 17 oder 21]
* Apache Maven [Versionsnummer, z.B. 3.8.x oder neuer]
* Git
* [Weitere Systemanforderungen, z.B. spezifische OS-Anforderungen, Python-Abhängigkeiten für Skripte]

### 4.2. Klonen des Repositories

```bash
git clone [https://github.com/DEIN_GITHUB_USERNAME/shark-scale.git](https://github.com/DEIN_GITHUB_USERNAME/shark-scale.git)
cd shark-scale
