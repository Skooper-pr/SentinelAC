package com.skooper.sentinelac.movement.model;

import java.util.Objects;

/**
 * Immutable 3D vector representing coordinates or velocities in Minecraft physics space.
 */
public final class Vector3D {

    public static final Vector3D ZERO = new Vector3D(0.0, 0.0, 0.0);

    private final double x;
    private final double y;
    private final double z;

    public Vector3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public Vector3D add(Vector3D other) {
        return new Vector3D(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Vector3D add(double dx, double dy, double dz) {
        return new Vector3D(this.x + dx, this.y + dy, this.z + dz);
    }

    public Vector3D subtract(Vector3D other) {
        return new Vector3D(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Vector3D multiply(double factor) {
        return new Vector3D(this.x * factor, this.y * factor, this.z * factor);
    }

    public Vector3D multiply(double fx, double fy, double fz) {
        return new Vector3D(this.x * fx, this.y * fy, this.z * fz);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double horizontalLengthSquared() {
        return x * x + z * z;
    }

    public double horizontalLength() {
        return Math.sqrt(horizontalLengthSquared());
    }

    public double distance(Vector3D other) {
        return Math.sqrt(distanceSquared(other));
    }

    public double distanceSquared(Vector3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public Vector3D normalize() {
        double len = length();
        if (len < 1.0E-6) {
            return ZERO;
        }
        return new Vector3D(x / len, y / len, z / len);
    }

    public double dot(Vector3D other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vector3D vector3D = (Vector3D) o;
        return Double.compare(vector3D.x, x) == 0 &&
               Double.compare(vector3D.y, y) == 0 &&
               Double.compare(vector3D.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return String.format("(%.4f, %.4f, %.4f)", x, y, z);
    }
}
