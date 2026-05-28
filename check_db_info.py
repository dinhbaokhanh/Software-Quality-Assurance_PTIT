import psycopg2
conn = psycopg2.connect(host="localhost",port=5433,database="online_learning",user="postgres",password="ptit")
cur = conn.cursor()

print("=== enrollments_type enum values ===")
cur.execute("SELECT enumlabel FROM pg_enum JOIN pg_type ON pg_enum.enumtypid = pg_type.oid WHERE pg_type.typname = 'enrollments_type'")
for r in cur.fetchall():
    print(" ", r[0])

print("\n=== currency enum values ===")
cur.execute("SELECT enumlabel FROM pg_enum JOIN pg_type ON pg_enum.enumtypid = pg_type.oid WHERE pg_type.typname = 'currency'")
for r in cur.fetchall():
    print(" ", r[0])

print("\n=== level-like enums ===")
cur.execute("SELECT pg_type.typname, enumlabel FROM pg_enum JOIN pg_type ON pg_enum.enumtypid = pg_type.oid WHERE pg_type.typname NOT IN ('enrollments_type','currency') ORDER BY pg_type.typname, enumlabel")
for r in cur.fetchall():
    print(f"  [{r[0]}] {r[1]}")

print("\n=== status enum for courses ===")
cur.execute("SELECT DISTINCT status FROM courses LIMIT 10")
for r in cur.fetchall():
    print(" ", r[0])

cur.close(); conn.close()
