# Guide technique — HCHuskHomesGUI

## Point d’entrée

Cible : Paper 26.3 (`26.3.build.+`), Java 25 sans preview. Compilation avec `-Xlint:all` ;
examiner les warnings avant de les attribuer au plugin ou à une dépendance.

Ce dépôt appartient à la suite privée d’usage HeavenCube, publiée comme source consultable.
Il dépend obligatoirement de HCCore. Lire d’abord [AGENTS.md](../AGENTS.md), puis le Core voisin.
Le [guide commun](https://github.com/HeavenCube/HCPlugins-Core/blob/main/docs/ECOSYSTEM.md) décrit les règles Java/Paper, les contrats Core,
le packaging et la CI. Ce guide local décrit les particularités à préserver ; le code reste l’autorité.

## Dépendances et compilation

HCCore et HuskHomes obligatoires ; PlaceholderAPI facultatif.

Cloner Core à côté. Shadow embarque InvUI et ConfigLib, relocalise leurs packages et hcconfig ; préserver notices tierces, resources, stratégie de doublons et tâche shadowJar.

```powershell
.\gradlew.bat build
```

Utiliser JDK 25. Sous Linux : `./gradlew build`. Le JAR est dans `build/libs/` ; installer aussi
les plugins serveur requis. Un clone Core modifié affecte le classpath local ; noter son commit.
Après extension d’API commune, construire Core séparément et installer sa version compatible en premier.

## Commandes et permissions

`/hcplugins huskhomesgui reload` : opérateurs. Le GUI est ouvert par l’intégration HuskHomes. Conserver les contrôles HuskHomes de téléportation/édition et le fonctionnement sans PlaceholderAPI.

La branche canonique est `/hcplugins huskhomesgui` ; elle est enregistrée chez Core, pas comme
une deuxième racine. Les résultats de reload, refus opérateur et autres textes partagés utilisent
`HCPluginsCore.translations(plugin)`. `{duration}` inclut déjà l’unité `ms`.

## Fichiers et données

`plugins/HCPlugins/HCHuskHomesGUI/{items,menus,locale}.yml`. GUI configurée dans GuiConfiguration. Préférences home (icône, favori, ordre manuel) dans les tags metadata HuskHomes `hchuskhomesgui:*`, pas dans une seconde base.

Valeurs par défaut dans `src/main/resources/`, jamais écrasées à chaque démarrage. Aucun import
automatique des anciens dossiers du monorepo. Messages communs dans `plugins/HCPlugins/translations.yml` ;
messages métier locaux. Modifier le fichier partagé se recharge avec `/hcplugins core reload`.

## Chemin d’exécution

HomeListListener prend les événements HuskHomes pour ouvrir HomesMenu. Les sessions utilisent la configuration GUI et MenuItemRenderer. HomeDialogs pilote les dialogues natifs ; les actions passent par l’API HuskHomes. HomePreferences enregistre les tags du home avec le propriétaire des données HuskHomes.

## Carte du code pour une modification

| Fichier | Responsabilité et points à préserver |
| --- | --- |
| [HCHuskHomesGUI.java](../src/main/java/fr/noltox/hcplugins/huskhomesgui/HCHuskHomesGUI.java) | API HuskHomes, listener, commande et shutdown GUI. |
| [GuiConfiguration.java](../src/main/java/fr/noltox/hcplugins/huskhomesgui/config/GuiConfiguration.java) | Chargement de trois fichiers, DTO ConfigLib et configuration runtime. |
| [MenuActions.java](../src/main/java/fr/noltox/hcplugins/huskhomesgui/config/MenuActions.java) | Définition/interprétation des actions métier du menu. |
| [HomesMenu.java](../src/main/java/fr/noltox/hcplugins/huskhomesgui/gui/HomesMenu.java) | Sessions, navigation, actions, reload et teardown. |
| [HomeDialogs.java](../src/main/java/fr/noltox/hcplugins/huskhomesgui/gui/HomeDialogs.java) | Dialogues natifs et contexte de session. |
| [HomePreferences.java](../src/main/java/fr/noltox/hcplugins/huskhomesgui/gui/HomePreferences.java) | Tags metadata propres à l’interface, item sérialisé et ordre. |
| [MenuItemRenderer.java](../src/main/java/fr/noltox/hcplugins/huskhomesgui/gui/MenuItemRenderer.java) | Rendu d’items et intégration PlaceholderAPI facultative. |
| [HomeListListener.java](../src/main/java/fr/noltox/hcplugins/huskhomesgui/listener/HomeListListener.java) | Entrée via événements HuskHomes. |
| [HCConfigurations.java](../src/main/java/fr/noltox/hcconfig/HCConfigurations.java) | Adaptation locale ConfigLib ; ne pas la généraliser sans second usage réel. |

`src/main/resources/paper-plugin.yml` définit identité, dépendances et permissions serveur.
`settings.gradle.kts` définit les builds composites ; `build.gradle.kts` le packaging.
`.github/workflows/build.yml` appelle les actions partagées à `@main` ; `.github/dependabot.yml`
maintient les dépendances. Une mise à jour de dépendance doit conserver ces contrats.

## Invariants et zones à risque

- Plugin serveur `HCHuskHomesGUI`, HCCore obligatoire ; module `huskhomesgui`.
- HCCore et HuskHomes obligatoires ; PlaceholderAPI facultatif.
- Ne pas ajouter cache de homes ou base de données indépendante : HuskHomes est propriétaire des homes.
- Préserver metadata icon/favorite/order et permissions HuskHomes ; ne pas contourner les refus de son API.
- Préserver invalidation/fermeture des sessions et callbacks au reload/disable.
- Conserver le JAR Shadow et ses relocations ; ne pas ajouter InvUI/ConfigLib à Core pour le seul besoin de cette GUI.

Avant une nouvelle logique transversale : chercher les usages dans Core et les autres plugins ;
ajouter au Core le contrat partagé réellement nécessaire avant le raccordement local. Ne pas recopier
un loader YAML, un registre de commandes ou un catalogue de traductions. Garder les événements et
états spécifiques ici. Thread serveur pour le jeu ; considérer callbacks et APIs tierces selon leur
thread réel, puis revalider le contexte avant mutation.

## Validation et limites

InvUI 2.5.1 est embarqué pour le support Minecraft 26.3. Le reload valide tous les fichiers,
invalide les callbacks de l’ancienne génération et ferme ses fenêtres avant d’activer le candidat.
Les filtres conservent leur ordre YAML, y compris lorsqu’ils remplacent le tri courant.
`GuiConfigurationTest` couvre le chargement ConfigLib et l’ordre/immutabilité des filtres.

`GuiConfigurationTest` existe. Pour packaging : contrôler présence des bibliothèques relocalisées et notices, absence d’APIs serveur embarquées. En jeu : GUI avec/sans PlaceholderAPI, navigation/recherche, éditions, favoris, icônes, permissions, reload avec GUI ouverte et fermeture du plugin.

Un test de configuration ne vérifie ni les actions HuskHomes, ni la persistance backend, ni les sessions réelles.

Documentation seule : vérifier les liens locaux et le diff. Modification runtime : build, tests ciblés,
et scénario serveur correspondant. Rapporter seulement ce qui a été exécuté, avec résultat et limite.
Pour transfert entre IA, donner le commit Core testé et les fichiers/changements encore non committés.
