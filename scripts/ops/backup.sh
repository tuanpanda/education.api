#!/usr/bin/env bash
# =============================================================================
# Sao luu EDUCATION tren may chu Linux: schema Oracle (expdp) + volume file dinh kem (Docker), SHA-256, xoa ban cu.
# Xem docs/deploy-internet.md (muc "Sao luu va khoi phuc"). Ban Windows: scripts/ops/backup.ps1.
#
#   EDU_DB_PASSWORD='<mat khau>' BACKUP_ROOT=/var/backups/education ./scripts/ops/backup.sh
#
# Bien (mac dinh trong ngoac):
#   BACKUP_ROOT (/var/backups/education)  RETENTION_DAYS (14)
#   ORACLE_CONNECT (localhost:1521/ORCL)  DB_USER (EDUCATION)  DB_SCHEMA (EDUCATION)
#   EXPDP (expdp trong PATH)  SQLPLUS (sqlplus trong PATH)
#   DATA_PUMP_DIRECTORY (DATA_PUMP_DIR)   DATA_PUMP_PATH (rong = hoi ALL_DIRECTORIES)
#   UPLOADS_VOLUME (education-prod-outputs)  HELPER_IMAGE (alpine:3.20)
#   SKIP_ORACLE=1 / SKIP_UPLOADS=1
# Mat khau KHONG nam tren dong lenh: lay tu EDU_DB_PASSWORD (hoac nhap an), ghi vao parfile tam chmod 600, xoa ngay.
# Quyen can co (DBA cap mot lan): GRANT READ, WRITE ON DIRECTORY DATA_PUMP_DIR TO EDUCATION;
# =============================================================================
set -euo pipefail
umask 077

BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/education}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
ORACLE_CONNECT="${ORACLE_CONNECT:-localhost:1521/ORCL}"
DB_USER="${DB_USER:-EDUCATION}"
DB_SCHEMA="${DB_SCHEMA:-EDUCATION}"
EXPDP="${EXPDP:-expdp}"
SQLPLUS="${SQLPLUS:-sqlplus}"
DATA_PUMP_DIRECTORY="${DATA_PUMP_DIRECTORY:-DATA_PUMP_DIR}"
DATA_PUMP_PATH="${DATA_PUMP_PATH:-}"
UPLOADS_VOLUME="${UPLOADS_VOLUME:-education-prod-outputs}"
HELPER_IMAGE="${HELPER_IMAGE:-alpine:3.20}"

STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="${BACKUP_ROOT}/${STAMP}"
mkdir -p "${TARGET}"
echo "Sao luu vao ${TARGET}"

TMP_FILES=()
cleanup() {
  local f
  for f in "${TMP_FILES[@]+"${TMP_FILES[@]}"}"; do
    rm -f -- "$f"
  done
}
trap cleanup EXIT

private_tmp() {
  local f
  f="$(mktemp "${TMPDIR:-/tmp}/edu-XXXXXXXX.par")"
  chmod 600 "$f"
  TMP_FILES+=("$f")
  printf '%s' "$f"
}

if [[ "${SKIP_ORACLE:-0}" != "1" ]]; then
  if [[ -z "${EDU_DB_PASSWORD:-}" ]]; then
    read -r -s -p "Mat khau Oracle cua ${DB_USER}: " EDU_DB_PASSWORD
    echo
  fi
  if [[ -z "${DATA_PUMP_PATH}" ]]; then
    sql="$(private_tmp)"
    cat > "$sql" <<SQL
SET HEADING OFF FEEDBACK OFF PAGESIZE 0 VERIFY OFF ECHO OFF
CONNECT ${DB_USER}/"${EDU_DB_PASSWORD}"@${ORACLE_CONNECT}
SELECT DIRECTORY_PATH FROM ALL_DIRECTORIES WHERE DIRECTORY_NAME = '${DATA_PUMP_DIRECTORY}';
EXIT
SQL
    DATA_PUMP_PATH="$("${SQLPLUS}" -S -L /nolog "@${sql}" | sed '/^[[:space:]]*$/d' | head -n 1 | tr -d '[:space:]')"
    [[ -n "${DATA_PUMP_PATH}" ]] || { echo "Khong doc duoc duong dan ${DATA_PUMP_DIRECTORY} (dat DATA_PUMP_PATH)" >&2; exit 1; }
  fi
  DUMP="education-${STAMP}.dmp"
  LOG="expdp-${STAMP}.log"
  par="$(private_tmp)"
  cat > "$par" <<PAR
userid=${DB_USER}/"${EDU_DB_PASSWORD}"@${ORACLE_CONNECT}
schemas=${DB_SCHEMA}
directory=${DATA_PUMP_DIRECTORY}
dumpfile=${DUMP}
logfile=${LOG}
flashback_time=SYSTIMESTAMP
exclude=STATISTICS
PAR
  echo "expdp schemas=${DB_SCHEMA} -> ${DATA_PUMP_DIRECTORY}/${DUMP}"
  set +e
  "${EXPDP}" "parfile=${par}"
  code=$?
  set -e
  rm -f -- "$par"
  unset EDU_DB_PASSWORD
  # 5 = hoan tat co canh bao (EX_SUCC_ERR): van giu file, kiem tra log.
  if [[ $code -ne 0 && $code -ne 5 ]]; then
    echo "expdp loi (${code}) - xem ${DATA_PUMP_PATH}/${LOG}" >&2
    exit 1
  fi
  mv -f -- "${DATA_PUMP_PATH}/${DUMP}" "${TARGET}/"
  cp -f -- "${DATA_PUMP_PATH}/${LOG}" "${TARGET}/"
fi

if [[ "${SKIP_UPLOADS:-0}" != "1" ]]; then
  docker volume inspect "${UPLOADS_VOLUME}" >/dev/null
  ARCHIVE="uploads-${STAMP}.tar.gz"
  echo "tar volume ${UPLOADS_VOLUME} -> ${ARCHIVE}"
  docker run --rm -v "${UPLOADS_VOLUME}:/data:ro" -v "${TARGET}:/backup" "${HELPER_IMAGE}" \
    tar czf "/backup/${ARCHIVE}" -C /data .
fi

(
  cd "${TARGET}"
  find . -maxdepth 1 -type f ! -name SHA256SUMS.txt -printf '%f\n' | sort | xargs -r sha256sum > SHA256SUMS.txt
)
echo "Da ghi SHA256SUMS.txt"

if [[ "${RETENTION_DAYS}" -gt 0 ]]; then
  find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -regextype posix-extended \
    -regex '.*/[0-9]{8}-[0-9]{6}' ! -path "${TARGET}" -mtime "+${RETENTION_DAYS}" -print -exec rm -rf -- {} +
fi

echo "Hoan tat: ${TARGET}"
