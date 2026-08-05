-- Dense, monotonic counter feeding the Feistel short-code codec (see FeistelShortCodeGenerator).
-- The counter itself is never exposed; the Feistel permutation maps it to a non-sequential code.
-- MINVALUE 0 / START 0 so the first code corresponds to counter 0 (the codec domain is 0-based).
CREATE SEQUENCE link_code_seq AS bigint MINVALUE 0 START WITH 0 INCREMENT BY 1;
