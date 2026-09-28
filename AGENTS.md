# Instructions — HCPlugins-HuskHomesGUI

## Démarrage et lecture ciblée

1. Lire ce fichier, `git status --short`, puis l’entrée pertinente de [docs/TECHNICAL.md](docs/TECHNICAL.md).
2. Avant toute modification technique, lire `../HCPlugins-Core/AGENTS.md` (ou le clone CI
`.hcplugins/HCPlugins-Core/AGENTS.md`) et inspecter l’API Core concernée. Si Core manque,
consulter [son guide](https://github.com/HeavenCube/HCPlugins-Core/blob/main/docs/ECOSYSTEM.md) ; ne pas deviner son contrat.
3. Rechercher avec `rg` dans les fichiers concernés et leurs tests. Lire le code réel avant de modifier.

## Contrat commun

- Java 25, sans preview ; conserver toolchain, release 25 et Paper déclaré par le build (26.2 actuellement).
- APIs publiques Paper modernes et Adventure en priorité ; vérifier signatures dans les sources/docs et compiler.
- Records, pattern matching, switch expressions et collections immuables quand utiles ; simplicité et mesure
  avant micro-optimisation. Virtual threads pour I/O indépendantes seulement, jamais pour état Bukkit.
- Pas de NMS/CraftBukkit/réflexion maison ni grosse dépendance sans nécessité et accord explicite.
- Avant une fonctionnalité dupliquée : réutiliser Core ; sinon ajouter la partie commune à Core d’abord,
  puis adapter les consommateurs. Core ne dépend pas du métier des plugins ; APIs Paper directes restent adaptées.
- HCCore seul possède `/hcplugins`. Utiliser son registre, ses traductions et helpers de fichiers/permissions.
- API Core compileOnly via build composite ; ne pas la republier Maven, l'embarquer ou la relocaliser chez un consommateur.
- État de jeu et registres sur thread serveur ; revalider activité/permission/session dans callbacks différés.
- Préserver reload candidat/rollback, PDC et contrats existants ; fermer tâches, callbacks et handles au disable.

## Invariants propres à ce dépôt

- Plugin serveur `HCHuskHomesGUI`, HCCore obligatoire ; module `huskhomesgui`.
- HCCore et HuskHomes obligatoires ; PlaceholderAPI facultatif.
- Ne pas ajouter cache de homes ou base de données indépendante : HuskHomes est propriétaire des homes.
- Préserver metadata icon/favorite/order et permissions HuskHomes ; ne pas contourner les refus de son API.
- Préserver invalidation/fermeture des sessions et callbacks au reload/disable.
- Conserver le JAR Shadow et ses relocations ; ne pas ajouter InvUI/ConfigLib à Core pour le seul besoin de cette GUI.

## Validation, Git et documentation

- Après Java/Gradle : `./gradlew build` ou `.\gradlew.bat build` sous PowerShell avec JDK 25.
  Choisir les tests ciblés selon le risque ; ne pas masquer une erreur ni désactiver une vérification.
- Docs seules : vérifier liens/chemins, faits et `git diff --check` ; pas de build coûteux sans raison concrète.
- Ne pas confondre build réussi, CI passée et résultat observé en jeu. Indiquer la validation manquante.
- Mettre à jour la section technique affectée ; conserver licence HeavenCube et notices tierces.
- Pas de commit/push/reset/rebase/stash/changement de branche/release/déploiement sans autorisation explicite.
  Une autorisation de session suffit ; préserver les modifications d’autres intervenants.

## Efficacité du contexte et restitution

- Charger seulement la section utile ; ne pas relire tous les dépôts/documents ni scanner build/.gradle.
- Regrouper lectures indépendantes, limiter les logs aux erreurs/résumés, éviter les recherches répétées.
- Résoudre une cause vérifiée, sans refactoring cosmétique ni tests qui recopient l’implémentation.
- Pas de sous-agents sans demande ou instruction applicable ; les réserver à des tâches réellement indépendantes.
- Poser une question uniquement pour une information bloquante ; poursuivre le travail déjà autorisé.
- Réponse finale en français : changement, validation exacte, action/limite restante ; quelques points courts.
  Pas de répétition du contexte ni de grands dumps de code. Résumer un handoff par objectif, fichiers/commits,
  état des tests, blocage et prochaine action ; ne pas stocker de secrets ou créer une mémoire permanente sans demande.
