package com.skooper.sentinelac.combat.model;

import com.skooper.sentinelac.movement.model.Vector3D;

import java.util.OptionalDouble;

/**
 * Axis-Aligned Bounding Box (AABB) in 3D space with slab-based ray intersection testing.
 */
public final class BoundingBox3D {

    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public BoundingBox3D(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public static BoundingBox3D fromCenterAndDimensions(Vector3D center, double width, double height) {
        double halfW = width / 2.0;
        return new BoundingBox3D(
                center.getX() - halfW,
                center.getY(),
                center.getZ() - halfW,
                center.getX() + halfW,
                center.getY() + height,
                center.getZ() + halfW
        );
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMinZ() {
        return minZ;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getMaxZ() {
        return maxZ;
    }

    public BoundingBox3D expand(double amount) {
        return new BoundingBox3D(
                minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount
        );
    }

    public boolean contains(Vector3D point) {
        return point.getX() >= minX && point.getX() <= maxX &&
               point.getY() >= minY && point.getY() <= maxY &&
               point.getZ() >= minZ && point.getZ() <= maxZ;
    }

    /**
     * Performs an exact 3D Ray-AABB intersection test using the Kay-Kajiya slab method.
     *
     * @param ray The directional ray.
     * @return OptionalDouble containing the intersection distance from ray origin, or empty if the ray misses the box.
     */
    public OptionalDouble rayIntersection(Ray3D ray) {
        Vector3D origin = ray.getOrigin();
        Vector3D dir = ray.getDirection();

        // If ray origin is inside the bounding box, distance is 0.0
        if (contains(origin)) {
            return OptionalDouble.of(0.0);
        }

        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;

        // X axis slab
        if (Math.abs(dir.getX()) > 1.0E-9) {
            double tx1 = (minX - origin.getX()) / dir.getX();
            double tx2 = (maxX - origin.getX()) / dir.getX();
            tMin = Math.max(tMin, Math.min(tx1, tx2));
            tMax = Math.min(tMax, Math.max(tx1, tx2));
        } else if (origin.getX() < minX || origin.getX() > maxX) {
            return OptionalDouble.empty();
        }

        // Y axis slab
        if (Math.abs(dir.getY()) > 1.0E-9) {
            double ty1 = (minY - origin.getY()) / dir.getY();
            double ty2 = (maxY - origin.getY()) / dir.getY();
            tMin = Math.max(tMin, Math.min(ty1, ty2));
            tMax = Math.min(tMax, Math.max(ty1, ty2));
        } else if (origin.getY() < minY || origin.getY() > maxY) {
            return OptionalDouble.empty();
        }

        // Z axis slab
        if (Math.abs(dir.getZ()) > 1.0E-9) {
            double tz1 = (minZ - origin.getZ()) / dir.getZ();
            double tz2 = (maxZ - origin.getZ()) / dir.getZ();
            tMin = Math.max(tMin, Math.min(tz1, tz2));
            tMax = Math.min(tMax, Math.max(tz1, tz2));
        } else if (origin.getZ() < minZ || origin.getZ() > maxZ) {
            return OptionalDouble.empty();
        }

        if (tMax < tMin || tMax < 0.0) {
            return OptionalDouble.empty();
        }

        double hitDist = tMin >= 0.0 ? tMin : tMax;
        return hitDist >= 0.0 ? OptionalDouble.of(hitDist) : OptionalDouble.empty();
    }

    @Override
    public String toString() {
        return String.format("[min=(%.2f, %.2f, %.2f), max=(%.2f, %.2f, %.2f)]",
                minX, minY, minZ, maxX, maxY, maxZ);
    }
}
