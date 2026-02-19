package edu.eci.arsw.blueprints.persistence;

import edu.eci.arsw.blueprints.model.Blueprint;
import edu.eci.arsw.blueprints.model.Point;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
@Primary
public class PostgresBlueprintPersistence implements BlueprintPersistence {

    private final String url = "jdbc:postgresql://localhost:5432/blueprintsdb";
    private final String user = "postgres";
    private final String password = "postgres";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public void saveBlueprint(Blueprint bp)
            throws BlueprintPersistenceException {

        try (Connection conn = getConnection()) {

            String insertBlueprint =
                    "INSERT INTO blueprint(author, name) VALUES (?, ?) RETURNING id";

            PreparedStatement ps =
                    conn.prepareStatement(insertBlueprint);

            ps.setString(1, bp.getAuthor());
            ps.setString(2, bp.getName());

            ResultSet rs = ps.executeQuery();
            rs.next();
            int blueprintId = rs.getInt("id");

            String insertPoint =
                    "INSERT INTO point(blueprint_id, x, y) VALUES (?, ?, ?)";

            PreparedStatement psPoint =
                    conn.prepareStatement(insertPoint);

            for (Point p : bp.getPoints()) {
                psPoint.setInt(1, blueprintId);
                psPoint.setInt(2, p.x());
                psPoint.setInt(3, p.y());
                psPoint.addBatch();
            }

            psPoint.executeBatch();

        } catch (SQLException e) {
            throw new BlueprintPersistenceException(
                    "Error saving blueprint: " + e.getMessage());
        }
    }

    @Override
    public Blueprint getBlueprint(String author, String name)
            throws BlueprintNotFoundException {

        try (Connection conn = getConnection()) {

            String queryBlueprint =
                    "SELECT id FROM blueprint WHERE author=? AND name=?";

            PreparedStatement ps =
                    conn.prepareStatement(queryBlueprint);

            ps.setString(1, author);
            ps.setString(2, name);

            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                throw new BlueprintNotFoundException("Blueprint not found");
            }

            int blueprintId = rs.getInt("id");

            String queryPoints =
                    "SELECT x, y FROM point WHERE blueprint_id=?";

            PreparedStatement psPoints =
                    conn.prepareStatement(queryPoints);

            psPoints.setInt(1, blueprintId);

            ResultSet rsPoints = psPoints.executeQuery();

            List<Point> points = new ArrayList<>();

            while (rsPoints.next()) {
                points.add(new Point(
                        rsPoints.getInt("x"),
                        rsPoints.getInt("y")
                ));
            }

            return new Blueprint(author, name, points);

        } catch (SQLException e) {
            throw new BlueprintNotFoundException(
                    "Error retrieving blueprint: " + e.getMessage());
        }
    }

    @Override
    public Set<Blueprint> getBlueprintsByAuthor(String author)
            throws BlueprintNotFoundException {

        Set<Blueprint> blueprints = new HashSet<>();

        try (Connection conn = getConnection()) {

            String query =
                    "SELECT name FROM blueprint WHERE author=?";

            PreparedStatement ps =
                    conn.prepareStatement(query);

            ps.setString(1, author);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String name = rs.getString("name");
                blueprints.add(getBlueprint(author, name));
            }

            return blueprints;

        } catch (SQLException e) {
            throw new BlueprintNotFoundException(
                    "Error retrieving blueprints by author: " + e.getMessage());
        }
    }

    @Override
    public Set<Blueprint> getAllBlueprints() {

        Set<Blueprint> blueprints = new HashSet<>();

        try (Connection conn = getConnection()) {

            String query =
                    "SELECT author, name FROM blueprint";

            PreparedStatement ps =
                    conn.prepareStatement(query);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String author = rs.getString("author");
                String name = rs.getString("name");
                try {
                    blueprints.add(getBlueprint(author, name));
                } catch (BlueprintNotFoundException e) {
                    // Skip blueprints that can't be found
                }
            }

            return blueprints;

        } catch (SQLException e) {
            // Return empty set on error
            return new HashSet<>();
        }
    }

    @Override
    public void addPoint(String author, String name, int x, int y)
            throws BlueprintNotFoundException {

        try (Connection conn = getConnection()) {

            // First verify the blueprint exists
            String queryBlueprint =
                    "SELECT id FROM blueprint WHERE author=? AND name=?";

            PreparedStatement ps =
                    conn.prepareStatement(queryBlueprint);

            ps.setString(1, author);
            ps.setString(2, name);

            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                throw new BlueprintNotFoundException("Blueprint not found");
            }

            int blueprintId = rs.getInt("id");

            // Insert the new point
            String insertPoint =
                    "INSERT INTO point(blueprint_id, x, y) VALUES (?, ?, ?)";

            PreparedStatement psPoint =
                    conn.prepareStatement(insertPoint);

            psPoint.setInt(1, blueprintId);
            psPoint.setInt(2, x);
            psPoint.setInt(3, y);
            psPoint.executeUpdate();

        } catch (SQLException e) {
            throw new BlueprintNotFoundException(
                    "Error adding point: " + e.getMessage());
        }
    }
}
