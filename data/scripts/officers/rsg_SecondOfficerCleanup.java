package data.scripts.officers;

import com.fs.starfarer.api.EveryFrameScript;

/** Periodically drops second-officer assignments that are no longer valid (ship stored, sold or lost, hullmod removed, etc). */
public class rsg_SecondOfficerCleanup implements EveryFrameScript {

    private static final float INTERVAL = 0.5f;

    private float timer = 0f;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        timer += amount;
        if (timer < INTERVAL) return;
        timer = 0f;
        rsg_SecondOfficers.prune();
    }
}
