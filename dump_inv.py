import sqlite3

conn = sqlite3.connect(r'c:\Users\teamr\Desktop\miencraft\shared-data\smp.db')
cur = conn.cursor()

# Most recent quit (id 2579) + last periodic (id 2537)
for snap_id in [2579, 2537]:
    cur.execute("SELECT id, name, source, created_at, yaml FROM inv_snapshots WHERE id = ?", (snap_id,))
    row = cur.fetchone()
    print(f"\n=== Snapshot ID {row[0]} | {row[1]} | {row[2]} ===")
    print(row[4])
    print("=== END ===")

conn.close()
