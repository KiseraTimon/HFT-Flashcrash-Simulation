import csv
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

times, mids, invs = [], [], []
with open("data/flagship_timeseries.csv") as f:
    r = csv.DictReader(f)
    for row in r:
        times.append(float(row["time_sec"]))
        mids.append(float(row["mid_price"]))
        invs.append(float(row["hft_agg_inventory"]))

vt, vv = [], []
with open("data/flagship_vpin.csv") as f:
    r = csv.DictReader(f)
    for row in r:
        vt.append(float(row["time_sec"]))
        vv.append(float(row["vpin"]))

fig, axes = plt.subplots(3, 1, figsize=(10, 9), sharex=True)

axes[0].plot(times, mids, color="#1f4e79", linewidth=1.2)
axes[0].axvline(300, color="gray", linestyle="--", linewidth=0.8, label="sell program starts")
axes[0].set_ylabel("E-mini mid price")
axes[0].set_title("Simulated Flash Crash Replication (seed=42) — vs. Kirilenko et al. (2017)")
axes[0].legend(loc="lower left", fontsize=8)
axes[0].grid(alpha=0.3)

axes[1].plot(times, invs, color="#a6541f", linewidth=1.0)
axes[1].axhline(120, color="gray", linestyle=":", linewidth=0.8, label="HFT hard inventory cap")
axes[1].set_ylabel("Aggregate |HFT\ninventory| (contracts)")
axes[1].legend(loc="upper left", fontsize=8)
axes[1].grid(alpha=0.3)

axes[2].plot(vt, vv, color="#5a1f7a", linewidth=1.0)
axes[2].axhline(0.45, color="gray", linestyle=":", linewidth=0.8, label="VPIN preemptive-halt threshold")
axes[2].set_ylabel("VPIN\n(order-flow toxicity)")
axes[2].set_xlabel("Simulated time (seconds)")
axes[2].legend(loc="upper left", fontsize=8)
axes[2].grid(alpha=0.3)

plt.tight_layout()
plt.savefig("data/flagship_summary.png", dpi=150)
print("saved data/flagship_summary.png")
