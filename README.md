# ScoDoc Prime

Appli Android (Kotlin + Jetpack Compose) pour consulter ses notes sur la passerelle **ScoDoc étudiant**, avec une notification à chaque nouvelle note.

## Installer
1. Va dans l'onglet **Releases** du dépôt (ou **Actions** → dernier build → artefact).
2. Télécharge `ScodocPrime.apk` sur ton téléphone et ouvre-le (autorise l'installation depuis des sources inconnues).

Chaque push recompile l'APK via GitHub Actions. Les nouvelles versions s'installent par-dessus l'ancienne (même clé de signature).

## Utilisation
- Au premier lancement, colle l'adresse de la page ScoDoc étudiant que tu utilises d'habitude.
- Connecte-toi dans la page qui s'ouvre (CAS de l'université), l'appli détecte la connexion toute seule (sinon bouton « Terminé »).
- Les notes sont vérifiées en arrière-plan toutes les ~30 min. Si la session CAS expire, une notif te demande de te reconnecter.

## Fonctionnement
L'appli utilise les mêmes endpoints que le site : `services/data.php?q=semestresEtudiant` et `q=relevéEtudiant&semestre=…`, avec les cookies de la connexion faite dans l'appli. Rien n'est envoyé ailleurs que sur le serveur ScoDoc.
