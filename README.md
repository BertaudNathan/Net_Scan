# Net Scan

Prototype Android de diagnostic réseau. La liste et le formulaire permettent de créer des visites stockées localement avec Room. Le nom du bâtiment est obligatoire ; le client et l'adresse sont facultatifs.

Toucher une visite ouvre son écran de scan :

- en haut, un petit aperçu de la caméra arrière (aucune image n'est enregistrée), le nom du bâtiment, l'opérateur de la SIM données avec sa technologie (4G, 5G…) et le bouton de fermeture ;
- au centre, l'emplacement de la future carte ARCore, encore un simple espace réservé ;
- en bas, le bouton **Scan réseau** : il mesure le réseau mobile réel pendant un minuteur (20, 30 ou 60 s) ou en chrono libre (20 s minimum). Il affiche le niveau INEXISTANT, FAIBLE, MOYEN ou HAUT sur un filtre coloré, puis enregistre la médiane dans la visite. Les valeurs brutes (RSRP, SINR, bande, cellule) sont repliées dans « Détails techniques » ;
- le bouton **Historique des mesures** permet de consulter les mesures enregistrées pour cette visite, de la plus récente à la plus ancienne, avec leur date, opérateur, durée, niveau et détails techniques ;
- le bouton **Rapport** ouvre l'écran du rapport PDF, généré hors ligne (voir plus bas) ;
- en bas, à droite par défaut, le bouton d'enregistrement (rond rouge pour démarrer, carré noir pour arrêter). Il est indépendant des scans réseau. Tant qu'ARCore n'est pas intégré, il ne mémorise pas encore le parcours.

Les seuils suivent la synthèse du projet : RSRP > −80 dBm ou SINR > 13 dB pour HAUT, < −100 dBm ou < 0 dB pour FAIBLE, et le pire des deux indicateurs est retenu. « Pas de service » donne INEXISTANT.

Le scan réseau demande la localisation précise (Android l'exige pour lire les informations de cellule) et l'accès au téléphone. L'aperçu demande la caméra. Une source de mesures simulée (`SimulatedSignalSource`) sert aux tests automatiques ; quand elle est utilisée, l'écran affiche « Données simulées ».

### Rapport PDF

L'écran **Rapport** génère un PDF A4 à partir des mesures enregistrées, sans connexion, puis en affiche l'aperçu. Il peut être téléchargé (l'utilisateur choisit l'emplacement, par exemple Téléchargements) ou envoyé par e-mail (choix de l'application, PDF en pièce jointe). Le PDF contient :

1. les informations de la visite et une synthèse client : nombre de points par niveau et points à couvrir en priorité ;
2. une page **plan** : un cadre réservé au plan des locaux, les pastilles P1, P2… à la couleur de leur niveau à y reporter, et un encadré pour le **placement de l'antenne** (emplacement, hauteur et fixation, alimentation, remarques) ;
3. le tableau des mesures (date, opérateur, technologie, niveau, durée) ;
4. une annexe technique (RSRP, SINR, bande, cellule, nombre de lectures, mode).

Les points sont numérotés dans l'ordre chronologique. Le plan n'est pas encore intégré automatiquement : le cadre est à compléter à la main. Seul le dernier PDF généré est conservé dans le cache de l'application ; il n'est envoyé nulle part sans action de l'utilisateur.

La carte et la heatmap ne sont pas encore disponibles.

## Lancer le projet

Ouvrir le projet dans Android Studio avec le SDK Android configuré, puis lancer la configuration `app` sur un appareil ou un émulateur compatible avec le `minSdk` du projet. En ligne de commande sous Windows :

```powershell
.\gradlew.bat :app:assembleDebug
```

Le thème peut être réglé sur clair, sombre ou automatique selon le système dans les préférences. La préférence gaucher/droitier place les boutons de fermeture et d'enregistrement à gauche ou à droite de l'écran et l'aperçu caméra du côté opposé ; les deux réglages sont conservés localement. L'écran de scan reste en portrait sur téléphone. Les visites, les scans réseau et les préférences restent sur l'appareil, sans synchronisation ni sauvegarde applicative, et sont supprimés lors de la désinstallation ou de l'effacement des données. La base n'est pas chiffrée par l'application ; évitez d'y saisir des informations sensibles avant la mise en place des protections adaptées.
