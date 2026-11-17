# Net Scan

Prototype Android de diagnostic réseau. La liste et le formulaire permettent de créer des visites stockées localement avec Room. Le nom du bâtiment est obligatoire ; le client et l'adresse sont facultatifs.

Toucher une visite ouvre son écran de scan :

- en haut, un petit aperçu de la caméra arrière (aucune image n'est enregistrée), le nom du bâtiment et l'opérateur de la SIM données avec sa technologie (4G, 5G…) ;
- au centre, l'emplacement de la future carte ARCore, encore un simple espace réservé ;
- en bas, le bouton **Scan réseau** : il mesure le réseau mobile réel pendant un minuteur (20, 30 ou 60 s) ou en chrono libre (20 s minimum). Il affiche le niveau INEXISTANT, FAIBLE, MOYEN ou HAUT sur un filtre coloré, puis enregistre la médiane dans la visite. Les valeurs brutes (RSRP, SINR, bande, cellule) sont repliées dans « Détails techniques » ;
- en bas à droite, le bouton d'enregistrement (rond rouge pour démarrer, carré noir pour arrêter). Il est indépendant des scans réseau. Tant qu'ARCore n'est pas intégré, il ne mémorise pas encore le parcours.

Les seuils suivent la synthèse du projet : RSRP > −80 dBm ou SINR > 13 dB pour HAUT, < −100 dBm ou < 0 dB pour FAIBLE, et le pire des deux indicateurs est retenu. « Pas de service » donne INEXISTANT.

Le scan réseau demande la localisation précise (Android l'exige pour lire les informations de cellule) et l'accès au téléphone. L'aperçu demande la caméra. Une source de mesures simulée (`SimulatedSignalSource`) sert aux tests automatiques ; quand elle est utilisée, l'écran affiche « Données simulées ».

Le rapport PDF, la carte et la heatmap ne sont pas encore disponibles.

## Lancer le projet

Ouvrir le projet dans Android Studio avec le SDK Android configuré, puis lancer la configuration `app` sur un appareil ou un émulateur compatible avec le `minSdk` du projet. En ligne de commande sous Windows :

```powershell
.\gradlew.bat :app:assembleDebug
```

L'interface Compose suit le thème clair ou sombre du système. L'écran de scan reste en portrait sur téléphone. Les visites et les scans réseau restent sur l'appareil, sans synchronisation ni sauvegarde applicative, et sont supprimés lors de la désinstallation ou de l'effacement des données. Tant que le projet est un prototype, un changement de schéma de la base efface les données existantes. La base n'est pas chiffrée par l'application ; évitez d'y saisir des informations sensibles avant la mise en place des protections adaptées. Les préférences utilisateur (dont le mode gaucher) restent à implémenter.
