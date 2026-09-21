-- The alert already stored its value and threshold but not which metric they referred to, which
-- left the evidence package unable to say what "0.25" actually measured.
ALTER TABLE alerts ADD COLUMN metric VARCHAR(30);
