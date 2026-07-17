-- Curated, hand-picked classification of the real production catalog (pulled read-only from
-- https://skycomposer.net/api/movies) into a small set of categories that highlight where a movie's
-- narrative genuinely echoes a Greek/Roman myth, a Bible story, a Shakespeare play, a canonical piece of
-- English-language literature, the Marvel universe, a fantasy archetype, or a classic stage tragedy, plus a
-- couple of well-documented cinematic styles. Every category reuses get_or_create_category() from V35, so
-- this file is idempotent and will not create a duplicate "Styles" (or any other) category if one already
-- exists under the right parent. Movies that no longer exist in the catalog by the time this runs are
-- silently skipped rather than failing the migration.
create or replace function link_movie_to_category(p_imdb_id varchar, p_category_id bigint) returns void
    language sql
as $$
    insert into movie_category (movie_id, category_id)
    select imdb_id, p_category_id from movies where imdb_id = p_imdb_id
    on conflict do nothing;
$$;

do $$
declare
    v_myth_id bigint;
    v_greek_id bigint;
    v_biblical_id bigint;
    v_shakespeare_id bigint;
    v_englit_id bigint;
    v_marvel_id bigint;
    v_fantasy_id bigint;
    v_theatre_id bigint;
    v_styles_id bigint;
    v_influences_id bigint;
    v_category_id bigint;
begin
    v_myth_id := get_or_create_category(null, 'Narratives & Myth', '📖');
    v_greek_id := get_or_create_category(v_myth_id, 'Greek & Roman Mythology', '🏛️');
    v_biblical_id := get_or_create_category(v_myth_id, 'Biblical Narratives', '✝️');
    v_shakespeare_id := get_or_create_category(v_myth_id, 'Shakespearean Drama', '🎭');
    v_englit_id := get_or_create_category(v_myth_id, 'English Classic Literature', '📚');
    v_marvel_id := get_or_create_category(v_myth_id, 'Marvel Universe', '🦸');
    v_fantasy_id := get_or_create_category(v_myth_id, 'Fantasy Archetypes', '🧙');
    v_theatre_id := get_or_create_category(v_myth_id, 'Classic Theatre & Tragedy', '🎪');
    v_styles_id := get_or_create_category(null, 'Styles', '🎨');
    v_influences_id := get_or_create_category(v_styles_id, 'Influences', '🖌️');

    -- Greek & Roman Mythology
    v_category_id := get_or_create_category(v_greek_id, 'Orpheus', null);
    perform link_movie_to_category('tt0054377', v_category_id); -- Testament of Orpheus

    v_category_id := get_or_create_category(v_greek_id, 'Medea', null);
    perform link_movie_to_category('tt0066065', v_category_id); -- Medea

    v_category_id := get_or_create_category(v_greek_id, 'Oedipus', null);
    perform link_movie_to_category('tt0364569', v_category_id); -- Oldboy

    v_category_id := get_or_create_category(v_greek_id, 'Dionysus', null);
    perform link_movie_to_category('tt0057831', v_category_id); -- Zorba the Greek

    v_category_id := get_or_create_category(v_greek_id, 'Icarus', null);
    perform link_movie_to_category('tt0068182', v_category_id); -- Aguirre, the Wrath of God

    v_category_id := get_or_create_category(v_greek_id, 'Odysseus', null);
    perform link_movie_to_category('tt0062622', v_category_id); -- 2001: A Space Odyssey
    perform link_movie_to_category('tt0190590', v_category_id); -- O Brother, Where Art Thou?

    v_category_id := get_or_create_category(v_greek_id, 'Prometheus', null);
    perform link_movie_to_category('tt15398776', v_category_id); -- Oppenheimer

    -- Biblical Narratives
    v_category_id := get_or_create_category(v_biblical_id, 'Noah', null);
    perform link_movie_to_category('tt1959490', v_category_id); -- Noah

    v_category_id := get_or_create_category(v_biblical_id, 'Jesus Christ', null);
    perform link_movie_to_category('tt0095497', v_category_id); -- The Last Temptation of Christ
    perform link_movie_to_category('tt0052618', v_category_id); -- Ben-Hur

    v_category_id := get_or_create_category(v_biblical_id, 'Fallen Angels', null);
    perform link_movie_to_category('tt0120655', v_category_id); -- Dogma

    v_category_id := get_or_create_category(v_biblical_id, 'Cain and Abel', null);
    perform link_movie_to_category('tt0071562', v_category_id); -- The Godfather Part II

    -- Shakespearean Drama
    v_category_id := get_or_create_category(v_shakespeare_id, 'Romeo and Juliet', null);
    perform link_movie_to_category('tt0117509', v_category_id); -- Romeo + Juliet

    v_category_id := get_or_create_category(v_shakespeare_id, 'King Lear', null);
    perform link_movie_to_category('tt0089881', v_category_id); -- Ran

    v_category_id := get_or_create_category(v_shakespeare_id, 'Macbeth', null);
    perform link_movie_to_category('tt0050613', v_category_id); -- Throne of Blood

    v_category_id := get_or_create_category(v_shakespeare_id, 'Hamlet', null);
    perform link_movie_to_category('tt0100519', v_category_id); -- Rosencrantz & Guildenstern Are Dead
    perform link_movie_to_category('tt0110357', v_category_id); -- The Lion King

    -- English Classic Literature
    v_category_id := get_or_create_category(v_englit_id, 'Kurtz', null);
    perform link_movie_to_category('tt0078788', v_category_id); -- Apocalypse Now (Heart of Darkness)

    v_category_id := get_or_create_category(v_englit_id, 'Atticus Finch', null);
    perform link_movie_to_category('tt35887611', v_category_id); -- To Kill A Mockingbird

    v_category_id := get_or_create_category(v_englit_id, 'Count Dracula', null);
    perform link_movie_to_category('tt0103874', v_category_id); -- Dracula
    perform link_movie_to_category('tt0013442', v_category_id); -- Nosferatu: A Symphony of Horror
    perform link_movie_to_category('tt5040012', v_category_id); -- Nosferatu (2024)

    v_category_id := get_or_create_category(v_englit_id, 'Frankenstein''s Creature', null);
    perform link_movie_to_category('tt0088247', v_category_id); -- The Terminator
    perform link_movie_to_category('tt0083658', v_category_id); -- Blade Runner
    perform link_movie_to_category('tt1856101', v_category_id); -- Blade Runner 2049

    -- Marvel Universe
    v_category_id := get_or_create_category(v_marvel_id, 'Iron Man', null);
    perform link_movie_to_category('tt0371746', v_category_id); -- Iron Man
    perform link_movie_to_category('tt1228705', v_category_id); -- Iron Man 2
    perform link_movie_to_category('tt1300854', v_category_id); -- Iron Man 3
    perform link_movie_to_category('tt0848228', v_category_id); -- The Avengers
    perform link_movie_to_category('tt4154756', v_category_id); -- Avengers: Infinity War
    perform link_movie_to_category('tt4154796', v_category_id); -- Avengers: Endgame

    v_category_id := get_or_create_category(v_marvel_id, 'Spider-Man', null);
    perform link_movie_to_category('tt4633694', v_category_id); -- Spider-Man: Into the Spider-Verse
    perform link_movie_to_category('tt9362722', v_category_id); -- Spider-Man: Across the Spider-Verse
    perform link_movie_to_category('tt10872600', v_category_id); -- Spider-Man: No Way Home

    v_category_id := get_or_create_category(v_marvel_id, 'Wolverine', null);
    perform link_movie_to_category('tt3315342', v_category_id); -- Logan
    perform link_movie_to_category('tt1430132', v_category_id); -- The Wolverine

    v_category_id := get_or_create_category(v_marvel_id, 'Doctor Doom', null);
    perform link_movie_to_category('tt21357150', v_category_id); -- Avengers: Doomsday

    -- Fantasy Archetypes
    v_category_id := get_or_create_category(v_fantasy_id, 'The Chosen One', null);
    perform link_movie_to_category('tt0241527', v_category_id); -- Harry Potter and the Sorcerer's Stone
    perform link_movie_to_category('tt0330373', v_category_id); -- Harry Potter and the Goblet of Fire
    perform link_movie_to_category('tt0304141', v_category_id); -- Harry Potter and the Prisoner of Azkaban
    perform link_movie_to_category('tt0373889', v_category_id); -- Harry Potter and the Order of the Phoenix
    perform link_movie_to_category('tt0417741', v_category_id); -- Harry Potter and the Half-Blood Prince
    perform link_movie_to_category('tt0926084', v_category_id); -- Harry Potter and the Deathly Hallows: Part 1
    perform link_movie_to_category('tt1201607', v_category_id); -- Harry Potter and the Deathly Hallows: Part 2
    perform link_movie_to_category('tt0120915', v_category_id); -- Star Wars: Episode I
    perform link_movie_to_category('tt0121765', v_category_id); -- Star Wars: Episode II
    perform link_movie_to_category('tt0121766', v_category_id); -- Star Wars: Episode III
    perform link_movie_to_category('tt0076759', v_category_id); -- Star Wars: Episode IV
    perform link_movie_to_category('tt0080684', v_category_id); -- Star Wars: Episode V
    perform link_movie_to_category('tt0086190', v_category_id); -- Star Wars: Episode VI
    perform link_movie_to_category('tt0087182', v_category_id); -- Dune (1984)
    perform link_movie_to_category('tt1160419', v_category_id); -- Dune: Part One
    perform link_movie_to_category('tt15239678', v_category_id); -- Dune: Part Two

    v_category_id := get_or_create_category(v_fantasy_id, 'The Hero''s Journey', null);
    perform link_movie_to_category('tt0120737', v_category_id); -- The Lord of the Rings: The Fellowship of the Ring
    perform link_movie_to_category('tt0167261', v_category_id); -- The Lord of the Rings: The Two Towers
    perform link_movie_to_category('tt0167260', v_category_id); -- The Lord of the Rings: The Return of the King
    perform link_movie_to_category('tt0903624', v_category_id); -- The Hobbit: An Unexpected Journey
    perform link_movie_to_category('tt1170358', v_category_id); -- The Hobbit: The Desolation of Smaug
    perform link_movie_to_category('tt2310332', v_category_id); -- The Hobbit: The Battle of the Five Armies

    -- Classic Theatre & Tragedy
    v_category_id := get_or_create_category(v_theatre_id, 'Blanche DuBois', null);
    perform link_movie_to_category('tt0044081', v_category_id); -- A Streetcar Named Desire

    v_category_id := get_or_create_category(v_theatre_id, 'Salieri', null);
    perform link_movie_to_category('tt0086879', v_category_id); -- Amadeus

    v_category_id := get_or_create_category(v_theatre_id, 'Willy Loman', null);
    perform link_movie_to_category('tt0104348', v_category_id); -- Glengarry Glen Ross

    v_category_id := get_or_create_category(v_theatre_id, 'Hamlet', null);
    perform link_movie_to_category('tt0100519', v_category_id); -- Rosencrantz & Guildenstern Are Dead (also a stage play)

    -- Styles / Influences
    v_category_id := get_or_create_category(v_influences_id, 'Film Noir', null);
    perform link_movie_to_category('tt0036775', v_category_id); -- Double Indemnity
    perform link_movie_to_category('tt0043014', v_category_id); -- Sunset Boulevard
    perform link_movie_to_category('tt0041959', v_category_id); -- The Third Man
    perform link_movie_to_category('tt0071315', v_category_id); -- Chinatown
    perform link_movie_to_category('tt0083658', v_category_id); -- Blade Runner

    v_category_id := get_or_create_category(v_influences_id, 'German Expressionism', null);
    perform link_movie_to_category('tt0010323', v_category_id); -- The Cabinet of Dr. Caligari
    perform link_movie_to_category('tt0017136', v_category_id); -- Metropolis
    perform link_movie_to_category('tt0013442', v_category_id); -- Nosferatu: A Symphony of Horror
    perform link_movie_to_category('tt0022100', v_category_id); -- M

    v_category_id := get_or_create_category(v_influences_id, 'French New Wave', null);
    perform link_movie_to_category('tt0053472', v_category_id); -- Breathless
    perform link_movie_to_category('tt0053198', v_category_id); -- The 400 Blows
    perform link_movie_to_category('tt0056663', v_category_id); -- Vivre sa vie
end;
$$;
