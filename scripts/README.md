# IAM Scripts

## Windows

| Script | Purpose |
| --- | --- |
| `dev.bat` / `dev.ps1` | Local developer stack: builds backend, starts auth-server, admin, and Vite. Uses local Redis and encrypted MySQL dev config. |
| `build.bat` / `build.ps1` | Builds backend and frontend artifacts without starting services. |
| `start.bat` / `start.ps1` | Starts the Docker Compose stack. |
| `stop.bat` / `stop.ps1` | Stops local Java/Vite processes and Docker Compose services. |

## Unix-like Shell

| Script | Purpose |
| --- | --- |
| `dev.sh` | Local developer stack counterpart for shell environments. |
| `build.sh` | Builds backend and frontend artifacts. |
| `start.sh` | Starts the Docker Compose stack. |
| `stop.sh` | Stops the Docker Compose stack. |

## Local Secrets

`dev.ps1` needs `IAM_CONFIG_KEY` to decrypt `ENC(...)` values in the dev Spring configuration. Lookup order:

1. `IAM_CONFIG_KEY` environment variable.
2. `scripts/.secrets/iam-config-key.txt`, encrypted by Windows DPAPI for the current user.
3. Hidden prompt, then save to `scripts/.secrets/iam-config-key.txt`.

`scripts/.secrets/` is ignored by git and must not be committed.
