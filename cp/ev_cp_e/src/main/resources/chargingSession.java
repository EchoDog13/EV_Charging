import java.time.Duration;
import java.util.UUID;

public class chargingSession {

    public String sessionId;
    public int driverId;
    public int chargerUID;

    public double unitsConsumed = 0;
    public double costPerUnit;
    public double totalCost;
    public Instant startTime;
    public Instant endTime;

    public String status;

    public chargingSession(int driverId, int chargerUID) {
        this.sessionId = UUID.randomUUID().toString();
        this.driverId = driverId;
        this.chargerUID = chargerUID;
        this.unitsConsumed = unitsConsumed;
        this.costPerUnit = costPerUnit;
        this.totalCost = unitsConsumed * costPerUnit;
        this.status = status;
    }

    public void endSession() {
        this.endTime = Instant.now();
        this.status = "ended";
        Duration duration = Duration.between(startTime, endTime);
        long seconds = duration.getSeconds();
        System.out.println("Charging session " + sessionId + " ended. Duration: " + seconds + " seconds. Total cost: $"
                + totalCost);
    }

}
