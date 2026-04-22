# Atlas — Wiki Joueur

## Sommaire

| Section | Description |
|---|---|
| [Faction](#faction) | Créer et gérer une faction : niveaux, XP, améliorations, coffres, revendications, alliés |
| [Métier](#métier) | Choisir une profession, compléter des quêtes quotidiennes, gagner de l'XP et des récompenses |
| [Donjon](#donjon) | Raider des donjons par vagues, affronter un boss et partager le butin entre factions |
| [Commandes de base](#commandes-de-base) | Maison, échange, téléportation et commandes utilitaires |

---

## Faction

### Qu'est-ce qu'une Faction ?

Une faction est un groupe persistant de joueurs qui combattent, progressent et gagnent des récompenses ensemble. Les contributions de chaque membre (quêtes de métier, donjons complétés) alimentent le pool d'XP partagé de la faction, ce qui fait monter son niveau et débloque de nouveaux avantages.

### Niveaux et XP

Les factions démarrent au **niveau 0**. L'XP requise pour atteindre le niveau suivant suit une formule à progression géométrique :

```
XP pour atteindre le niveau 1  = 100
XP pour atteindre le niveau N  = ceil(XP(N-1) × 1.09)
```

Le niveau maximum d'une faction est **100**.

L'XP est gagnée en complétant des quêtes de métier (via le système de quêtes quotidiennes) ou en terminant des donjons. Elle est ajoutée automatiquement au pool de la faction — aucune action manuelle n'est requise.

### Améliorations

À certains niveaux de faction, une **amélioration** devient disponible. Chaque amélioration octroie deux bonus :

- **Bonus de PV** — augmente les PV maximum des Atlas Cristaux (Golems de Fer) de votre faction
- **Coffre de faction** — débloque un ou plusieurs coffres doubles virtuels accessibles par tous les membres

Lorsqu'un seuil d'amélioration est atteint, l'amélioration apparaît comme **en attente** dans `/faction upgrade list`. Un officier ou le propriétaire doit l'appliquer avec `/faction upgrade apply` pour recevoir le bonus de PV sur les cristaux.

> Les coffres débloqués par une amélioration sont accessibles immédiatement sans application ; seul le bonus de PV nécessite l'étape d'application explicite.

### Coffre de Faction

Le coffre de faction est un espace de stockage virtuel (coffre double) partagé entre tous les membres. Le nombre de coffres disponibles dépend du nombre de seuils d'amélioration atteints. Utilisez `/faction upgrade list` pour voir le nombre actuel.

Accédez au coffre de faction via l'interface de l'Atlas Cristal (clic droit sur un cristal de faction).

### Protection contre la Régression de Niveau

Si le niveau d'une faction descend en dessous d'un seuil d'amélioration (suite à une action d'administrateur), les coffres au-delà du nouveau maximum sont **automatiquement détruits** — leur contenu est jeté au sol à l'emplacement du cristal.

### Revendications et Avant-poste

Les factions peuvent revendiquer un territoire grâce aux **avant-postes**. Un chunk revendiqué empêche les joueurs non membres ou non alliés de construire ou de casser des blocs à l'intérieur. Utilisez `/faction outpost` pour consulter et gérer les revendications.

### Système d'Alliance

Deux factions peuvent devenir alliées. Les factions alliées :
- Ne se **font pas** de dégâts entre elles (les tirs amis sont désactivés)
- N'attaquent **pas** les entités de donjon de l'autre faction
- Peuvent optionnellement partager la messagerie de faction

Pour former une alliance, les propriétaires ou chefs des deux factions doivent être d'accord :

```
/faction ally <faction>       — envoyer une demande d'alliance
/faction allyaccept <faction> — accepter une demande en attente
/faction allydeny  <faction>  — refuser une demande en attente
/faction unally <faction>     — dissoudre une alliance existante
```

### Commandes de Faction

| Commande | Description | Permission |
|---|---|---|
| `/faction` | Afficher le menu d'aide | `atlas.faction` |
| `/faction create <nom>` | Créer une nouvelle faction | `atlas.faction.create` |
| `/faction disband` | Dissoudre votre faction (propriétaire uniquement) | `atlas.faction.disband` |
| `/faction rename <nom>` | Renommer votre faction | `atlas.faction.rename` |
| `/faction description <texte>` | Définir la description de la faction | `atlas.faction.description` |
| `/faction color <couleur>` | Changer la couleur du chat de faction | `atlas.faction.color` |
| `/faction invite <joueur>` | Inviter un joueur dans votre faction | `atlas.faction.invite` |
| `/faction accept` | Accepter une invitation de faction | `atlas.faction.accept` |
| `/faction decline` | Refuser une invitation de faction | `atlas.faction.decline` |
| `/faction leave` | Quitter votre faction actuelle | `atlas.faction.leave` |
| `/faction kick <joueur>` | Expulser un membre de la faction | `atlas.faction.kick` |
| `/faction promote <joueur>` | Promouvoir un membre au rang supérieur | `atlas.faction.promote` |
| `/faction demote <joueur>` | Rétrograder un membre au rang inférieur | `atlas.faction.demote` |
| `/faction transfer <joueur>` | Transférer la propriété de la faction | `atlas.faction.transfer` |
| `/faction info [faction]` | Afficher les informations d'une faction | `atlas.faction.info` |
| `/faction list` | Lister toutes les factions | `atlas.faction.list` |
| `/faction members` | Lister les membres et rôles de votre faction | `atlas.faction.members` |
| `/faction home` | Se téléporter au foyer de faction (défini via l'atlas cristal) | `atlas.faction.home` |
| `/faction upgrade list` | Afficher les améliorations disponibles et en attente | `atlas.faction.upgrade` |
| `/faction upgrade apply` | Appliquer une amélioration en attente sur vos cristaux | `atlas.faction.upgrade` |
| `/faction ally <faction>` | Envoyer une demande d'alliance | — |
| `/faction allyaccept <faction>` | Accepter une demande d'alliance | — |
| `/faction allydeny <faction>` | Refuser une demande d'alliance | — |
| `/faction unally <faction>` | Dissoudre une alliance | — |
| `/faction msg <texte>` | Envoyer un message aux membres en ligne de votre faction | `atlas.faction.msg` |

### Rôles des Membres

| Rôle | Permissions |
|---|---|
| **Membre** | Accès de base, aucune gestion |
| **Modérateur** | Inviter, expulser des membres |
| **Chef** | Toutes les actions de Modérateur + renommer, couleur, description |
| **Propriétaire** | Contrôle total, y compris dissoudre et transférer la propriété |

---

## Métier

### Qu'est-ce qu'un Métier ?

Chaque joueur peut exercer l'un des quatre métiers disponibles. Votre métier détermine les quêtes quotidiennes que vous recevez et la façon dont vous contribuez à l'XP de votre faction. La progression du métier (niveau + XP) est personnelle ; l'XP de faction gagnée grâce aux récompenses de quêtes est partagée.

### Métiers Disponibles

| Métier | Outil | Spécialité |
|---|---|---|
| **Mineur** | Pioche en Fer | Miner des minerais et de la pierre |
| **Bûcheron** | Hache en Fer | Couper des arbres |
| **Chasseur** | Arc | Tuer des mobs |
| **Fermier** | Graines de Blé | Récolter des cultures |

Attribuez ou changez votre métier en interagissant avec le PNJ correspondant dans le monde.

> Changer de métier réinitialise votre XP et niveau de métier actuels et déclenche un **temps de recharge de 24 heures** avant de pouvoir changer à nouveau.

### Quêtes Quotidiennes

Chaque jour, trois quêtes sont générées pour vous en fonction de votre métier. Vous pouvez sélectionner et activer **jusqu'à 2** des trois quêtes proposées par jour. Une fois 2 quêtes sélectionnées, le quota quotidien est verrouillé jusqu'au lendemain.

Chaque quête contient **1 ou 2 tâches** tirées aléatoirement du pool de tâches de votre métier, calibrées selon un niveau de difficulté aléatoire :

| Difficulté | Nom | Description |
|---|---|---|
| 1 | Facile | Petites quantités, peu de temps |
| 2 | Normal | Quantités modérées |
| 3 | Difficile | Grandes quantités |
| 4 | Hardcore | Quantités élevées, peu de temps |

La progression est suivie automatiquement pendant le jeu. Un message en barre d'action affiche votre avancement par tâche en temps réel. Lorsque toutes les tâches d'une quête sont complétées, les récompenses sont accordées automatiquement.

### Formule de Récompense en XP de Faction

Compléter une quête accorde de l'XP directement au pool de votre faction :

```
XP Faction = récompenseBase × sommeDifficulté × √(niveauFaction + 1)
```

Où :
- `récompenseBase` = 10 (valeur par défaut du serveur, configurable)
- `sommeDifficulté` = somme des valeurs de difficulté de toutes les tâches de la quête
- `niveauFaction` = niveau actuel de votre faction au moment de la complétion

Des niveaux de faction élevés et des quêtes plus difficiles multiplient considérablement la récompense.

### Récompenses en Objets

Chaque tâche peut fournir des récompenses en objets qui évoluent avec la difficulté. Les objets sont placés directement dans votre inventaire ; tout surplus est jeté à vos pieds.

### Jokeyrini — Le PNJ de Quête Spéciale

**Jokeyrini** est un PNJ spécial disponible indépendamment du système de métiers. Une fois par jour, Jokeyrini propose une quête unique avec 2 à 3 tâches en difficulté **Difficile** (niveau 3) ou **Hardcore** (niveau 4). Il y a **15% de chance** que l'offre quotidienne soit une **Quête Spéciale Légendaire** — 3 tâches toutes en difficulté 5.

À la complétion, Jokeyrini récompense des **Clés de Donjon**, utilisées pour démarrer un donjon :

| Type de quête | Clés reçues |
|---|---|
| Difficile (difficulté max 3) | 1 clé |
| Hardcore (difficulté max 4) | 2 clés |
| Spéciale Légendaire | 3 clés |

La progression des quêtes Jokeyrini est affichée dans la barre d'action avec le préfixe `[Jokeyrini]`.

### Commandes de Métier

Interagissez directement avec les PNJ de métier pour accéder à l'interface du métier. Il n'y a pas de commandes de chat pour la gestion des quêtes quotidiennes — tout se fait via l'interface en jeu.

---

## Donjon

### Qu'est-ce qu'un Donjon ?

Les donjons sont des structures pré-construites placées dans le monde par un administrateur. Chaque donjon alterne entre les états **inactif** et **actif**. Lorsqu'il est actif, un joueur possédant une **Clé de Donjon** peut entrer et démarrer le donjon. Celui-ci enchaîne alors une série de **vagues** d'ennemis, se terminant par une **vague de boss**.

### Types de Donjons

| Type | Thème |
|---|---|
| **Trial** | Donjon classique |
| **Desert** | Ruines du désert |
| **Nether Castle** | Forteresse du Nether |
| **Plains** | Plaines ouvertes |
| **Sky** | Île céleste |
| **Ocean** | Ruines sous-marines |

Chaque type possède son propre ensemble de mobs, types de boss et tables de butin.

### Niveau

Lorsqu'un donjon devient actif, un niveau aléatoire compris entre **0 et 99** lui est attribué. Le niveau détermine :
- Le nombre de vagues (évolue entre le min et le max définis pour le type)
- Le nombre de mobs par vague
- Les multiplicateurs de PV et de dégâts des ennemis
- Le montant d'XP de faction accordé

Plus le niveau est élevé = plus de mobs, des ennemis plus résistants et plus d'XP.

### Rareté

Chaque activation de donjon se voit également attribuer une **rareté**, tirée d'un pool pondéré :

| Rareté | Couleur | Multiplicateur d'XP | Probabilité |
|---|---|---|---|
| Commun | Blanc | ×1.0 | 66% |
| Rare | Cyan | ×1.5 | 14% |
| Épique | Violet clair | ×2.5 | 10% |
| Légendaire | Or | ×5.0 | 5% |
| Mystique | Rouge | ×10.0 | 3% |
| Déesse | Jaune | ×20.0 | 2% |

La rareté multiplie l'XP totale distribuée à la fin du donjon.

### Vagues

Un donjon se déroule en plusieurs vagues successives. Chaque vague doit être entièrement éliminée avant que la suivante ne commence. Après avoir nettoyé une vague, vous disposez de **5 secondes** avant que la vague suivante ne démarre.

La **dernière vague** est toujours une **Vague de Boss** : moins d'ennemis, mais bien plus résistants. Un son et un titre spéciaux annoncent la vague de boss à son début.

Si tous les joueurs quittent le donjon (aucun joueur détecté à l'intérieur), le donjon se réinitialise automatiquement — la progression et les kills sont perdus.

### Démarrer un Donjon

1. Obtenir une **Clé de Donjon** (via les quêtes de Jokeyrini)
2. Trouver un donjon **actif** (annoncé à l'ensemble du serveur lors de son activation)
3. Entrer dans la structure du donjon avec la clé en main — faire un clic droit sur le générateur d'épreuves démarrera le donjon
4. Survivre à toutes les vagues et vaincre le boss

### Distribution de l'XP

L'XP totale est calculée ainsi :

```
XP totale = (expBase + niveau/99 × (expMax - expBase)) × multiplicateurRareté
```

À la fin du donjon, l'XP est distribuée entre toutes les factions ayant infligé des dégâts au **boss**, proportionnellement aux dégâts infligés par chaque faction :

```
Part de la faction = XP totale × (dégâts du boss de la faction / dégâts totaux du boss)
```

Cela signifie que plusieurs factions peuvent participer au même donjon et chacune reçoit une part proportionnelle des récompenses. La faction ayant infligé le plus de dégâts au boss est annoncée comme la faction victorieuse.

### Drop de la Clé d'Épreuve Omineuse

Lorsqu'un donjon de **rareté Épique ou supérieure** est complété, il y a une chance qu'une **Clé d'Épreuve Omineuse** enchantée tombe au centre du donjon. Cette clé peut être utilisée pour **forcer l'activation** d'un donjon, garantissant une activation de rareté Légendaire, Mystique ou Déesse.

La probabilité de drop évolue avec la rareté : les donjons plus rares ont une chance plus élevée.

### Système d'Alliance dans les Donjons

Les factions alliées combattant dans le même donjon ne **se font pas** de dégâts entre elles. Cela permet aux factions alliées de coopérer à l'intérieur d'un donjon sans risquer les tirs amis. Le statut d'alliance est vérifié au moment de chaque attaque.

---

## Commandes de Base

### /home et /sethome

Définissez un foyer personnel nommé et téléportez-vous-y à tout moment.

```
/sethome <nom>   — enregistre votre position actuelle comme foyer
/home            — se téléporte vers votre premier foyer (le plus ancien)
/home <nom>      — se téléporte vers un foyer nommé spécifique
```

- La téléportation lance un **compte à rebours de 5 secondes**. Se déplacer annule la téléportation.
- Il y a un **temps de recharge de 30 secondes** entre les téléportations.
- Plusieurs foyers nommés sont supportés.

### /trade

Initiez un échange d'objets pair à pair avec un autre joueur.

```
/trade <joueur>  — envoyer une demande d'échange à un joueur
```

Les deux joueurs voient une interface dans laquelle ils peuvent déposer des objets dans leurs emplacements respectifs. Les deux joueurs doivent cliquer sur **Accepter** avant qu'un compte à rebours de 3 secondes valide l'échange. Chaque joueur peut annuler à tout moment avant la fin du compte à rebours.

### /tpa

Demandez à vous téléporter chez un autre joueur.

```
/tpa <joueur>    — envoyer une demande de téléportation
/tpa accept      — accepter une demande reçue (ou cliquer sur [Accepter] dans le chat)
/tpa deny        — refuser une demande reçue (ou cliquer sur [Refuser] dans le chat)
```

- Les demandes expirent après **30 secondes** si elles ne reçoivent pas de réponse.
- Les téléportations acceptées lancent un **compte à rebours de 10 secondes**. Recevoir des dégâts annule la téléportation.
- Il y a un **temps de recharge de 30 secondes** entre les demandes.

### /spawn

Se téléporter au point d'apparition du monde.

```
/spawn
```

- Lance un **compte à rebours de 10 secondes**. Se déplacer ou recevoir des dégâts l'annule.
- Possède un **temps de recharge de 30 secondes**.
- Les joueurs dans un rayon de **64 blocs** du spawn sont protégés contre le PvP.

### /rtp

Se téléporter aléatoirement vers un endroit sûr dans un rayon de 1024 blocs autour du spawn du monde.

```
/rtp
```

- Lance un **compte à rebours de 5 secondes**. Se déplacer annule la téléportation.
- Possède un **temps de recharge de 30 secondes**.
- La destination est toujours un emplacement sûr avec un sol solide et de l'air au-dessus.
