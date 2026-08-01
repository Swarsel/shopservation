# shopservation

Android companion for [shopservatory](https://github.com/Swarsel/shopservatory).

## BSetup

```sh
SHOPSERVATION_KEYSTORE_PASS_FILE=<password file> nix run .#init-keys
```

Publish with:

```sh
SHOPSERVATION_DOCS=docs SHOPSERVATION_KEYSTORE_PASS_FILE=<password file> nix run .#publish
```
