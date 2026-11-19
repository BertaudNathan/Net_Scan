# Net Scan

Prototype Android de diagnostic réseau. La liste et le formulaire permettent de créer des visites stockées localement avec Room. Le nom du bâtiment est obligatoire ; le client et l'adresse sont facultatifs.

Toucher une visite ouvre son écran de scan :

- en haut, un petit aperçu de ce que filme ARCore (aucune image n'est enregistrée), le nom du bâtiment et l'opérateur de la SIM données avec sa technologie (4G, 5G…) et le bouton de fermeture ;
- au centre, la carte du parcours vue de dessus, avec la heatmap du réseau (voir plus bas) ;
- en bas, le bouton **Scan réseau** : il mesure le réseau mobile réel pendant un minuteur (20, 30 ou 60 s) ou en chrono libre (20 s minimum). Il affiche le niveau INEXISTANT, FAIBLE, MOYEN ou HAUT sur un filtre coloré, puis enregistre la médiane dans la visite. Les valeurs brutes (RSRP, SINR, bande, cellule) sont repliées dans « Détails techniques ». Lancer un scan démarre l'enregistrement s'il est arrêté, pour que la mesure soit placée sur la carte ;
- le bouton **Historique des mesures** permet de consulter les mesures enregistrées pour cette visite, de la plus récente à la plus ancienne, avec leur date, opérateur, durée, niveau et détails techniques ;
- le bouton **Rapport** ouvre l'écran du rapport PDF, généré hors ligne (voir plus bas) ;
- en bas, à droite par défaut, le bouton d'enregistrement (rond rouge pour démarrer, carré noir pour arrêter). Il enregistre le parcours suivi par ARCore.

### Carte et heatmap (paliers 1 et 2)

ARCore suit le téléphone hors ligne, sans Cloud Anchors ni Geospatial : seules des positions et des points de la scène sont enregistrés, jamais d'image. La carte est une grille de cases de 50 cm vue de dessus, la direction de départ vers le haut :

- deux fois par seconde, la profondeur ARCore (ou, à défaut, son nuage de points) donne des points de ce que filme la caméra, jusqu'à 4 m. La profondeur est projetée avec les paramètres de l'image ARCore (image CPU ou texture affichée) dont le cadrage correspond au sien. Le sol vient du plus bas des plans horizontaux détectés, ou à défaut de la hauteur la plus fréquente des points bas ; tant qu'il est inconnu, rien n'est cartographié. Les cases entre le téléphone et un point vu sont libres (avec une tolérance sur la hauteur du sol qui grandit avec la distance) ; une case devient mur ou obstacle quand ce qu'on y voit, entre 0,3 et 2 m du sol, s'élève d'au moins 40 cm, ce qui écarte les plateaux de table. Chaque case cumule ces indices : un reflet isolé ne crée pas de mur, et revoir le sol à travers une case la libère ;
- la zone accessible est le sol vu libre plus la trajectoire élargie de 50 cm, moins les obstacles ; une case par laquelle on est passé n'est jamais un obstacle. Rien n'est estimé en dehors ;
- chaque scan réseau est placé à la position médiane du téléphone pendant la mesure (restez immobile) ;
- une case mesurée prend la couleur pleine de son niveau ; les autres sont estimées par pondération inverse au carré de la distance (IDW), avec une distance de parcours dans la zone, pas à vol d'oiseau : une estimation ne traverse pas un mur. Elles sont d'autant plus pâles qu'elles sont loin d'une mesure ;
- au-delà de 6 m de parcours de toute mesure, la case reste hachurée (inconnue). Les murs et obstacles sont en plein foncé.

Chaque enregistrement est un parcours à part, avec son propre repère ARCore : au repos, l'écran montre le dernier parcours sauvegardé ; un nouvel enregistrement commence une nouvelle carte. Une perte de suivi (mouvement trop rapide, manque de lumière, mur uni, caméra tournée vers le sol) ou un saut de plus d'1,5 m entre deux positions coupe la trajectoire en tronçons qui ne sont pas reliés entre eux. Le téléphone vibre à chaque perte pendant un enregistrement. ARCore repart alors souvent d'une origine décalée : la nouvelle portion est mise en attente (bandeau « Recalage… »), le temps d'en voir quelques murs, puis recalée sur la carte déjà tracée en cherchant le petit déplacement (jusqu'à 1,5 m et 12°) qui superpose le mieux ses murs aux anciens sans faire tomber de mur sur du sol déjà vu libre. Les grandes corrections sont pénalisées : dans un couloir uniforme, seul le décalage latéral est corrigé. Sans murs communs (pièce nouvelle), la portion est ajoutée telle quelle après 10 s. La géolocalisation n'aiderait pas : à l'intérieur, son erreur (5 à 30 m) dépasse de loin la dérive d'ARCore. La légende de la carte s'ouvre avec le bouton « i ».

ARCore est facultatif : sur un téléphone non compatible, ou sans les Services Google Play pour la RA, les scans réseau restent possibles mais ne sont pas placés sur la carte. Limites connues : la dérive d'ARCore sur les longues distances n'est pas corrigée, les parcours d'une même visite ne se superposent pas, la carte ne sait pas ce qu'est un obstacle (béton, rayonnage, chambre froide : annotations à venir), et les vitres, l'inox ou l'obscurité peuvent créer des trous ou de faux obstacles.

### Mode manuel : plan importé

À la création d'une visite, on choisit le mode : **ARCore** (la carte se dessine en filmant) ou **plan manuel**. Le choix est définitif pour la visite. En mode manuel, la caméra n'est pas utilisée et rien n'est enregistré par ARCore :

1. importer le plan du bâtiment, image ou PDF (première page), par exemple une photo du plan d'évacuation. Il est copié dans le stockage privé de l'application, réduit à 2560 pixels de côté, et ne quitte pas l'appareil ;
2. régler l'échelle : toucher les deux extrémités d'une longueur connue (mur, porte), puis saisir cette longueur en mètres. Le bouton « Échelle » permet de la régler à nouveau ;
3. si on le souhaite, tracer les murs (bouton « Murs ») : toucher le début d'un mur puis sa fin ; le toucher suivant termine le mur suivant, qui part de là, et « Nouveau mur » lève le crayon. Une extrémité touchée près d'un mur existant s'y accroche (à son extrémité ou le long de lui), et un mur presque horizontal ou vertical est redressé : les murs se rejoignent sans laisser de passage. « Annuler le point » retire le dernier point touché ; l'outil « Effacer » supprime le mur touché, après confirmation. Chaque mur est enregistré dès qu'il est tracé ;
4. avant chaque scan réseau, toucher sa position sur le plan (cible). Le bouton « Scan réseau » reste inactif tant qu'aucune position n'est choisie, et la position est effacée après chaque mesure, puisqu'on se déplace ensuite.

Les scans et les murs sont enregistrés à leur place sur le plan, en fraction de sa largeur et de sa hauteur : régler l'échelle à nouveau ne les déplace pas. La heatmap est la même qu'en mode ARCore (cases de 50 cm, estimation IDW) : les cases traversées par un mur tracé sont des obstacles, et les estimations les contournent par les passages laissés libres (portes) au lieu de les traverser. Sans mur tracé, elles vont à vol d'oiseau. Seules les cases à moins de 6 m d'un scan sont colorées, le reste du plan reste visible tel quel. On zoome en pinçant (le zoom aide à tracer précisément) et on revient à la vue d'ensemble en touchant deux fois.

Les seuils suivent la synthèse du projet : RSRP > −80 dBm ou SINR > 13 dB pour HAUT, < −100 dBm ou < 0 dB pour FAIBLE, et le pire des deux indicateurs est retenu. « Pas de service » donne INEXISTANT.

Le scan réseau demande la localisation précise (Android l'exige pour lire les informations de cellule) et l'accès au téléphone. ARCore demande la caméra, et lit le gyroscope et l'accéléromètre à haute fréquence (`HIGH_SAMPLING_RATE_SENSORS`, accordée sans demande à l'utilisateur) : sans elle, Android 12+ bride ces capteurs et le suivi décroche. Des sources simulées de mesures (`SimulatedSignalSource`) et de positions (`SimulatedPositionSource`) servent aux tests automatiques ; quand l'une d'elles est utilisée, l'écran affiche « Données simulées ».

### Rapport PDF

L'écran **Rapport** génère un PDF A4 à partir des mesures enregistrées, sans connexion, puis en affiche l'aperçu. Il peut être téléchargé (l'utilisateur choisit l'emplacement, par exemple Téléchargements) ou envoyé par e-mail (choix de l'application, PDF en pièce jointe). Le PDF contient :

1. les informations de la visite et une synthèse client : nombre de points par niveau et points à couvrir en priorité ;
2. une page **plan** : un cadre réservé au plan des locaux, les pastilles P1, P2… à la couleur de leur niveau à y reporter, et un encadré pour le **placement de l'antenne** (emplacement, hauteur et fixation, alimentation, remarques) ;
3. le tableau des mesures (date, opérateur, technologie, niveau, durée) ;
4. une annexe technique (RSRP, SINR, bande, cellule, nombre de lectures, mode).

Les points sont numérotés dans l'ordre chronologique. La carte ARCore et la heatmap ne sont pas encore reprises dans le PDF : le cadre du plan est à compléter à la main. Seul le dernier PDF généré est conservé dans le cache de l'application ; il n'est envoyé nulle part sans action de l'utilisateur.

La nature des obstacles (béton, rayonnage, chambre froide) n'est pas encore annotée : un mur tracé ou vu par la caméra arrête les estimations, quelle que soit son épaisseur.

## Lancer le projet

Ouvrir le projet dans Android Studio avec le SDK Android configuré, puis lancer la configuration `app` sur un appareil ou un émulateur compatible avec le `minSdk` du projet. En ligne de commande sous Windows :

```powershell
.\gradlew.bat :app:assembleDebug
```

Le thème peut être réglé sur clair, sombre ou automatique selon le système dans les préférences. La préférence gaucher/droitier place les boutons de fermeture et d'enregistrement à gauche ou à droite de l'écran et l'aperçu caméra du côté opposé ; les deux réglages sont conservés localement. L'écran de scan reste en portrait sur téléphone. Les visites, les parcours, les scans réseau et les préférences restent sur l'appareil, sans synchronisation ni sauvegarde applicative, et sont supprimés lors de la désinstallation ou de l'effacement des données. La base n'est pas chiffrée par l'application ; évitez d'y saisir des informations sensibles avant la mise en place des protections adaptées.

## Tests et intégration continue

En local :

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Les tests instrumentés demandent un appareil ou un émulateur API 36 connecté.

Le workflow GitHub Actions [`.github/workflows/android.yml`](.github/workflows/android.yml) se lance à chaque push sur `main` et `feature/**`, à chaque pull request vers `main`, et à la main depuis l'onglet Actions :

1. **Tests unitaires, lint et APK** : `testDebugUnitTest`, `lintDebug` (non bloquant pour l'instant, rapport publié) et `assembleDebug`. L'APK debug est téléchargeable dans les artefacts pendant 14 jours.
2. **Tests instrumentés** : `connectedDebugAndroidTest` sur un émulateur API 36 sans caméra. Aucun test ne lit le vrai modem : les écrans utilisent la source simulée et le parcours complet écrit ses mesures directement en base.
3. **Release** : un tag `v*` (par exemple `v0.2.0`) publie l'APK debug dans une pré-release GitHub, après la réussite des deux étapes précédentes.

Les rapports de tests (HTML et XML) sont joints à chaque exécution, même en cas d'échec. Les tests ARCore, qui demandent un vrai téléphone et des déplacements, ne font pas partie de la CI : `MapScanScreenTest` utilise `SimulatedPositionSource`, et le parcours complet tourne sur un émulateur sans ARCore, donc sans carte.
