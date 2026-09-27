# education.api

Spring Boot 3.3 / Java 21 / Oracle Hybrid API.

## Docker production

Moi image deu dung profile `prod`. Build tu dong khi co Pull Request (GitHub Actions self-hosted tren Docker Desktop).

### Build tay

```powershell
.\scripts\docker-build-prod.ps1
docker compose up -d
```

Image tags: `education-api:prod`, `education-api:sha-<commit>`.

### CI (Pull Request + version tag)

1. Mo Docker Desktop.
2. Dang ky runner (token lay o GitHub: Settings → Actions → Runners → New self-hosted runner):

```powershell
.\scripts\register-self-hosted-runner.ps1 -Token "<RUNNER_TOKEN>"
C:\actions-runner\education-api\run.cmd
```

3. Mo Pull Request: workflow **Docker production build** tao image:

| Su kien | Tag |
| --- | --- |
| Pull Request `#12` | `education-api:prod-pr-12`, `prod-pr-12-<sha>`, `prod-build-<run>`, `prod` |
| Git tag `v1.2.0` | `education-api:v1.2.0`, `prod-v1.2.0`, `prod` |
| Push `main` | `education-api:prod`, `prod-build-<run>`, `sha-<sha>` |

Phat hanh version:

```powershell
git tag v1.0.1
git push origin v1.0.1
```

Oracle tren may host: container ket noi `host.docker.internal:1521`. Copy `.env.example` thanh `.env` neu can doi thong tin DB.
