package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V8__backfill_transitive_movie_challenge_closure extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            rebuildDirectResultsWithoutCycles(context);

            int inserted;
            do {
                inserted = statement.executeUpdate("""
                        insert into user_movie_winner_loser_all (user_id, winner_id, loser_id)
                        select distinct ancestor.user_id, ancestor.winner_id, descendant.loser_id
                        from user_movie_winner_loser_all ancestor
                        join user_movie_winner_loser_all descendant
                            on descendant.user_id = ancestor.user_id
                            and descendant.winner_id = ancestor.loser_id
                        where ancestor.winner_id <> descendant.loser_id
                            and not exists (
                                select 1
                                from user_movie_winner_loser_all existing
                                where existing.user_id = ancestor.user_id
                                    and existing.winner_id = ancestor.winner_id
                                    and existing.loser_id = descendant.loser_id
                            )
                        """);
            } while (inserted > 0);

            try (ResultSet cycles = statement.executeQuery("""
                    select count(1)
                    from user_movie_winner_loser_all forward_relation
                    join user_movie_winner_loser_all reverse_relation
                        on reverse_relation.user_id = forward_relation.user_id
                        and reverse_relation.winner_id = forward_relation.loser_id
                        and reverse_relation.loser_id = forward_relation.winner_id
                    """)) {
                cycles.next();
                if (cycles.getLong(1) > 0) {
                    throw new FlywayException("Existing movie challenge data contains winner-loser cycles");
                }
            }

            statement.executeUpdate("drop table movie_user_votes");
            statement.executeUpdate("drop table user_movie_pair_challenge");
        }
    }

    private void rebuildDirectResultsWithoutCycles(Context context) throws Exception {
        List<WinnerLoser> acceptedResults = new ArrayList<>();
        Map<String, Map<String, Set<String>>> graphByUser = new HashMap<>();

        try (Statement statement = context.getConnection().createStatement();
             ResultSet directResults = statement.executeQuery("""
                     select pair_challenge.user_id,
                         case when pair_challenge.movie1_wins then pair_challenge.movie1_id else pair_challenge.movie2_id end as winner_id,
                         case when pair_challenge.movie1_wins then pair_challenge.movie2_id else pair_challenge.movie1_id end as loser_id
                     from user_movie_pair_challenge pair_challenge
                     order by pair_challenge.user_id, pair_challenge.movie1_id, pair_challenge.movie2_id
                     """)) {
            while (directResults.next()) {
                WinnerLoser result = new WinnerLoser(
                        directResults.getString("user_id"),
                        directResults.getString("winner_id"),
                        directResults.getString("loser_id"));

                Map<String, Set<String>> userGraph = graphByUser.computeIfAbsent(result.userId(), ignored -> new HashMap<>());
                if (!createsCycle(userGraph, result.winnerId(), result.loserId())) {
                    userGraph.computeIfAbsent(result.winnerId(), ignored -> new HashSet<>()).add(result.loserId());
                    acceptedResults.add(result);
                }
            }
        }

        try (Statement statement = context.getConnection().createStatement()) {
            statement.executeUpdate("delete from user_movie_winner_loser_all");
            statement.executeUpdate("delete from user_movie_winner_loser");
        }

        try (PreparedStatement directInsert = context.getConnection().prepareStatement("""
                     insert into user_movie_winner_loser (user_id, winner_id, loser_id)
                     values (?, ?, ?)
                     """);
             PreparedStatement closureInsert = context.getConnection().prepareStatement("""
                     insert into user_movie_winner_loser_all (user_id, winner_id, loser_id)
                     values (?, ?, ?)
                     """)) {
            for (WinnerLoser result : acceptedResults) {
                bind(directInsert, result);
                directInsert.addBatch();
                bind(closureInsert, result);
                closureInsert.addBatch();
            }
            directInsert.executeBatch();
            closureInsert.executeBatch();
        }
    }

    private boolean createsCycle(Map<String, Set<String>> graph, String winnerId, String loserId) {
        if (winnerId.equals(loserId)) {
            return true;
        }

        ArrayDeque<String> nodesToVisit = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        nodesToVisit.add(loserId);

        while (!nodesToVisit.isEmpty()) {
            String current = nodesToVisit.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(winnerId)) {
                return true;
            }
            nodesToVisit.addAll(graph.getOrDefault(current, Set.of()));
        }

        return false;
    }

    private void bind(PreparedStatement statement, WinnerLoser result) throws Exception {
        statement.setString(1, result.userId());
        statement.setString(2, result.winnerId());
        statement.setString(3, result.loserId());
    }

    private record WinnerLoser(String userId, String winnerId, String loserId) {
    }
}
