ALTER TABLE V1_SUBSUMSJON_BRUKT DROP CONSTRAINT v1_subsumsjon_brukt_id_fkey;
ALTER TABLE V1_SUBSUMSJON_BRUKT ADD CONSTRAINT v1_subsumsjon_brukt_id_fkey FOREIGN KEY (id) REFERENCES v1_subsumsjon (id) ON DELETE CASCADE;