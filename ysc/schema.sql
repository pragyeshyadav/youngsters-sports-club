


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE EXTENSION IF NOT EXISTS "pg_graphql" WITH SCHEMA "graphql";






CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";





SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."frame_players" (
    "id" integer NOT NULL,
    "frame_id" integer,
    "user_id" integer,
    "player_name" character varying(100),
    "created_at" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "check_player_identity" CHECK ((("user_id" IS NOT NULL) OR ("player_name" IS NOT NULL)))
);


ALTER TABLE "public"."frame_players" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."frame_players_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."frame_players_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."frame_players_id_seq" OWNED BY "public"."frame_players"."id";



CREATE TABLE IF NOT EXISTS "public"."frames" (
    "id" integer NOT NULL,
    "table_id" integer,
    "started_by" integer,
    "approved_by" integer,
    "start_time" timestamp without time zone NOT NULL,
    "end_time" timestamp without time zone,
    "duration_minutes" integer,
    "total_amount" numeric(10,2),
    "status" character varying(20) NOT NULL,
    "payment_status" character varying(20) DEFAULT 'UNPAID'::character varying,
    "created_at" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "frames_duration_minutes_check" CHECK (("duration_minutes" >= 0)),
    CONSTRAINT "frames_payment_status_check" CHECK ((("payment_status")::"text" = ANY ((ARRAY['UNPAID'::character varying, 'PAID'::character varying, 'PARTIAL'::character varying])::"text"[]))),
    CONSTRAINT "frames_status_check" CHECK ((("status")::"text" = ANY ((ARRAY['STARTED'::character varying, 'ENDED'::character varying, 'PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::"text"[]))),
    CONSTRAINT "frames_total_amount_check" CHECK (("total_amount" >= (0)::numeric))
);


ALTER TABLE "public"."frames" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."frames_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."frames_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."frames_id_seq" OWNED BY "public"."frames"."id";



CREATE TABLE IF NOT EXISTS "public"."payments" (
    "id" integer NOT NULL,
    "frame_id" integer,
    "user_id" integer,
    "amount" numeric(10,2) NOT NULL,
    "payment_method" character varying(50),
    "status" character varying(20) DEFAULT 'SUCCESS'::character varying,
    "paid_at" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "payments_amount_check" CHECK (("amount" >= (0)::numeric)),
    CONSTRAINT "payments_payment_method_check" CHECK ((("payment_method")::"text" = ANY ((ARRAY['CASH'::character varying, 'UPI'::character varying, 'CARD'::character varying])::"text"[])))
);


ALTER TABLE "public"."payments" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."payments_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."payments_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."payments_id_seq" OWNED BY "public"."payments"."id";



CREATE TABLE IF NOT EXISTS "public"."snooker_tables" (
    "id" integer NOT NULL,
    "table_name" character varying(50) NOT NULL,
    "rate_per_minute" numeric(10,2) NOT NULL,
    "is_active" boolean DEFAULT true,
    "created_at" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "snooker_tables_rate_per_minute_check" CHECK (("rate_per_minute" >= (0)::numeric))
);


ALTER TABLE "public"."snooker_tables" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."snooker_tables_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."snooker_tables_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."snooker_tables_id_seq" OWNED BY "public"."snooker_tables"."id";



CREATE TABLE IF NOT EXISTS "public"."user_dues" (
    "id" integer NOT NULL,
    "user_id" integer,
    "total_due" numeric(10,2) DEFAULT 0,
    "last_updated" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "user_dues_total_due_check" CHECK (("total_due" >= (0)::numeric))
);


ALTER TABLE "public"."user_dues" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."user_dues_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."user_dues_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."user_dues_id_seq" OWNED BY "public"."user_dues"."id";



CREATE TABLE IF NOT EXISTS "public"."users" (
    "id" integer NOT NULL,
    "google_id" character varying(255) NOT NULL,
    "name" character varying(150),
    "email" character varying(150) NOT NULL,
    "phone" character varying(20) NOT NULL,
    "role" character varying(20) NOT NULL,
    "profile_pic" "text",
    "is_active" boolean DEFAULT true,
    "created_at" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "users_role_check" CHECK ((("role")::"text" = ANY ((ARRAY['CUSTOMER'::character varying, 'ADMIN'::character varying, 'SUPER_ADMIN'::character varying])::"text"[])))
);


ALTER TABLE "public"."users" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."users_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."users_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."users_id_seq" OWNED BY "public"."users"."id";



ALTER TABLE ONLY "public"."frame_players" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."frame_players_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."frames" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."frames_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."payments" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."payments_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."snooker_tables" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."snooker_tables_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."user_dues" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."user_dues_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."users" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."users_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."frame_players"
    ADD CONSTRAINT "frame_players_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."frames"
    ADD CONSTRAINT "frames_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."payments"
    ADD CONSTRAINT "payments_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."snooker_tables"
    ADD CONSTRAINT "snooker_tables_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."snooker_tables"
    ADD CONSTRAINT "snooker_tables_table_name_key" UNIQUE ("table_name");



ALTER TABLE ONLY "public"."user_dues"
    ADD CONSTRAINT "user_dues_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."user_dues"
    ADD CONSTRAINT "user_dues_user_id_key" UNIQUE ("user_id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_email_key" UNIQUE ("email");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_google_id_key" UNIQUE ("google_id");



ALTER TABLE ONLY "public"."users"
    ADD CONSTRAINT "users_pkey" PRIMARY KEY ("id");



CREATE INDEX "idx_frames_status" ON "public"."frames" USING "btree" ("status");



CREATE INDEX "idx_frames_table" ON "public"."frames" USING "btree" ("table_id");



CREATE INDEX "idx_payments_user" ON "public"."payments" USING "btree" ("user_id");



CREATE INDEX "idx_users_email" ON "public"."users" USING "btree" ("email");



ALTER TABLE ONLY "public"."frame_players"
    ADD CONSTRAINT "frame_players_frame_id_fkey" FOREIGN KEY ("frame_id") REFERENCES "public"."frames"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."frame_players"
    ADD CONSTRAINT "frame_players_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."frames"
    ADD CONSTRAINT "frames_approved_by_fkey" FOREIGN KEY ("approved_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."frames"
    ADD CONSTRAINT "frames_started_by_fkey" FOREIGN KEY ("started_by") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."frames"
    ADD CONSTRAINT "frames_table_id_fkey" FOREIGN KEY ("table_id") REFERENCES "public"."snooker_tables"("id");



ALTER TABLE ONLY "public"."payments"
    ADD CONSTRAINT "payments_frame_id_fkey" FOREIGN KEY ("frame_id") REFERENCES "public"."frames"("id");



ALTER TABLE ONLY "public"."payments"
    ADD CONSTRAINT "payments_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id");



ALTER TABLE ONLY "public"."user_dues"
    ADD CONSTRAINT "user_dues_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id");





ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";


GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";








































































































































































GRANT ALL ON TABLE "public"."frame_players" TO "anon";
GRANT ALL ON TABLE "public"."frame_players" TO "authenticated";
GRANT ALL ON TABLE "public"."frame_players" TO "service_role";



GRANT ALL ON SEQUENCE "public"."frame_players_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."frame_players_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."frame_players_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."frames" TO "anon";
GRANT ALL ON TABLE "public"."frames" TO "authenticated";
GRANT ALL ON TABLE "public"."frames" TO "service_role";



GRANT ALL ON SEQUENCE "public"."frames_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."frames_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."frames_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."payments" TO "anon";
GRANT ALL ON TABLE "public"."payments" TO "authenticated";
GRANT ALL ON TABLE "public"."payments" TO "service_role";



GRANT ALL ON SEQUENCE "public"."payments_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."payments_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."payments_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."snooker_tables" TO "anon";
GRANT ALL ON TABLE "public"."snooker_tables" TO "authenticated";
GRANT ALL ON TABLE "public"."snooker_tables" TO "service_role";



GRANT ALL ON SEQUENCE "public"."snooker_tables_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."snooker_tables_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."snooker_tables_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."user_dues" TO "anon";
GRANT ALL ON TABLE "public"."user_dues" TO "authenticated";
GRANT ALL ON TABLE "public"."user_dues" TO "service_role";



GRANT ALL ON SEQUENCE "public"."user_dues_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."user_dues_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."user_dues_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."users" TO "anon";
GRANT ALL ON TABLE "public"."users" TO "authenticated";
GRANT ALL ON TABLE "public"."users" TO "service_role";



GRANT ALL ON SEQUENCE "public"."users_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."users_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."users_id_seq" TO "service_role";









ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";































