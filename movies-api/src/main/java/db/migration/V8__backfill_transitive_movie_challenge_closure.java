package db.migration;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V8__backfill_transitive_movie_challenge_closure extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
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
}
