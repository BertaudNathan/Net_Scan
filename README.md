# Net Scan

Prototype Android de diagnostic réseau. Cette version présente uniquement la liste et le formulaire de création : les exemples sont fictifs, les données ne sont pas enregistrées et aucune mesure réseau n'est réalisée.

## Lancer le projet

Ouvrir le projet dans Android Studio avec le SDK Android configuré, puis lancer la configuration `app` sur un appareil ou un émulateur compatible avec le `minSdk` du projet. En ligne de commande sous Windows :

```powershell
.\gradlew.bat :app:assembleDebug
```

L'interface Compose suit le thème clair ou sombre du système. La persistance Room, les préférences utilisateur, la gestion réelle des scans et les mesures sont prévues pour des étapes ultérieures.