-- Drop only quiz-related tables that changed, so Hibernate can recreate them
DO $$
BEGIN
    -- Drop dependent table first
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'question_options') THEN
        EXECUTE 'DROP TABLE question_options CASCADE';
    END IF;

    -- Drop questions table
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'questions') THEN
        EXECUTE 'DROP TABLE questions CASCADE';
    END IF;

    -- Drop categories table (new domain)
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'categories') THEN
        EXECUTE 'DROP TABLE categories CASCADE';
    END IF;
END $$;

-- Note: session_questions and quiz_sessions remain; they will continue to work
--       since the mapping does not enforce a FK to questions.question_id.

