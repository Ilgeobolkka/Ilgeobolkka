#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
    echo "사용법: $0 <mvp|history-heavy>" >&2
    exit 1
fi

dataset_name=$1
case "$dataset_name" in
    mvp)
        printf '%s\n' '{
  "name": "mvp",
  "books": 100,
  "pages": 400,
  "readers": 1000,
  "newReaders": 334,
  "activeRentalReaders": 333,
  "ownedReaders": 333,
  "inkPurchases": 1000,
  "pageRentals": 333,
  "inkLedgerEntries": 1333,
  "libraryEntries": 666
}'
        ;;
    history-heavy)
        actual_counts=$(cat)
        expected_counts='ink_purchase	6000
page_rental	500333
ink_ledger	506333
reader	1000
book	100
book_page	400
library_entry	666'
        if [ "$actual_counts" != "$expected_counts" ]; then
            echo "history-heavy 행 수가 예상과 다릅니다." >&2
            printf '%s\n' "$actual_counts" >&2
            exit 1
        fi
        printf '%s\n' '{
  "name": "history-heavy",
  "books": 100,
  "pages": 400,
  "readers": 1000,
  "newReaders": 334,
  "activeRentalReaders": 333,
  "ownedReaders": 333,
  "inkPurchases": 6000,
  "pageRentals": 500333,
  "inkLedgerEntries": 506333,
  "libraryEntries": 666
}'
        ;;
    *)
        echo "알 수 없는 성능 데이터셋입니다: $dataset_name" >&2
        exit 1
        ;;
esac
