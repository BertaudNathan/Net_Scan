# Net Scan

Prototype Android de diagnostic réseau. La liste et le formulaire permettent de créer des visites stockées localement avec Room. Le nom du bâtiment est obligatoire ; le client et l'adresse sont facultatifs. Aucune mesure réseau, carte ou rapport n'est encore disponible.

## Lancer le projet

Ouvrir le projet dans Android Studio avec le SDK Android configuré, puis lancer la configuration `app` sur un appareil ou un émulateur compatible avec le `minSdk` du projet. En ligne de commande sous Windows :

```powershell
.\gradlew.bat :app:assembleDebug
```

L'interface Compose suit le thème clair ou sombre du système. Les visites restent sur l'appareil, sans synchronisation ni sauvegarde applicative, et sont supprimées lors de la désinstallation ou de l'effacement des données. La base n'est pas chiffrée par l'application ; évitez d'y saisir des informations sensibles avant la mise en place des protections adaptées. Les préférences utilisateur et les mesures restent à implémenter.