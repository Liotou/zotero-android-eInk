# Zotero E-Ink

Version personnelle de Zotero pour Android, pensée pour les liseuses à encre électronique
(Boox Note 5C). Elle s'installe **à côté** de l'application Zotero officielle (paquet
`org.zotero.android.eink`, nom « Zotero E-Ink ») et se synchronise avec le même compte Zotero.

## Installation sur la tablette (une seule fois)

1. **Activer GitHub Actions sur la fourchette** : onglet *Actions* du dépôt sur github.com,
   puis bouton *I understand my workflows, go ahead and enable them*. Sans cela, aucun APK
   n'est fabriqué.
2. **Installer Obtainium** sur la Boox, depuis
   <https://github.com/ImranR98/Obtainium/releases> (fichier APK `arm64-v8a`). Android demande
   d'autoriser l'installation d'applications depuis le navigateur : accepter.
3. Dans Obtainium : *Ajouter une application*, URL
   `https://github.com/Liotou/zotero-android-eInk`, activer l'option **Include prereleases**
   (« inclure les préversions », pour recevoir les versions de test), puis *Ajouter* et
   *Installer*.
4. Ouvrir **Zotero E-Ink** et se connecter avec l'identifiant Zotero habituel : la
   bibliothèque se télécharge depuis le cloud Zotero.

Sans Obtainium, l'APK se télécharge aussi directement depuis la page *Releases* du dépôt
(fichier `zotero-eink.apk`). Dernière version stable :
<https://github.com/Liotou/zotero-android-eInk/releases/latest/download/zotero-eink.apk>

## Mises à jour

Chaque modification envoyée sur GitHub fabrique automatiquement un nouvel APK
(compter 15 à 20 minutes) :

| Branche | Publication | Usage |
| --- | --- | --- |
| `master` | version normale | version stable du quotidien |
| `claude/...` | préversion « test » | essais successifs avec Claude |

Dans Obtainium, actualiser la liste puis toucher *Mettre à jour*. Les données locales et la
connexion sont conservées d'une version à l'autre. Seules les 10 dernières préversions sont
gardées.

## À savoir

- **Synchronisation** : même serveur que l'application officielle (`api.zotero.org`), la
  connexion crée une clé d'API dédiée visible sur <https://www.zotero.org/settings/keys>.
  La synchronisation des fichiers via WebDAV fonctionne de la même façon.
- **Coexistence** : l'application officielle et Zotero E-Ink ont chacune leur copie locale ;
  les deux restent synchronisées avec le même compte, on peut donc garder l'officielle en
  secours.
- **Lecteur PDF** : tous les PDF s'ouvrent dans le lecteur open source de Zotero (celui de
  Zotero pour ordinateur, fondé sur pdf.js) et non dans le moteur commercial PSPDFKit, qui
  exige une licence.
- **Pas de rapport de plantage** envoyé à Zotero, ni de bandeau proposant la version officielle.

## Signature de l'APK

Android n'accepte une mise à jour que si elle est signée avec la même clé que la version
installée. Par défaut, les APK sont signés avec la clé de développement publique fournie par
Zotero dans ce dépôt (`debug.keystore`) : c'est suffisant pour un usage personnel, mais
n'importe qui pourrait signer un APK avec cette clé.

Pour utiliser une clé privée, **avant la première installation** (changer de clé plus tard
impose de désinstaller puis réinstaller, et donc de resynchroniser) :

```sh
keytool -genkeypair -v -keystore eink.keystore -alias eink -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 eink.keystore   # sous macOS : base64 -i eink.keystore
```

puis créer dans *Settings > Secrets and variables > Actions* du dépôt les secrets
`EINK_KEYSTORE_BASE64` (sortie de `base64`), `EINK_KEYSTORE_PASSWORD`, `EINK_KEY_ALIAS`
(`eink`) et `EINK_KEY_PASSWORD`. Conserver `eink.keystore` en lieu sûr.

## Compilation locale

```sh
git submodule update --init --recursive
for s in scripts/bundle_*; do case "$s" in *_local.sh) ;; *.py) python3 "$s" ;; *.sh) bash "$s" ;; esac; done
./gradlew assembleInternalEink
```

L'APK se trouve dans `app/build/outputs/apk/internal/eink/`.
