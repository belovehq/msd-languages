
-- :name h2-select-words :? :*
SELECT WORDID, WORD FROM MSDWORDS;

-- :name h2-select-words-stems :? :*
SELECT WORDID, STEM, WORD FROM MSDWORDS;


-- :name h2-select-cowords :? :*
SELECT m2.wordid, count(m2.trackid)
  FROM matrix m1
  INNER JOIN  matrix m2 on m1.trackid = m2.trackid
  WHERE m1.wordid = :wordid -- AND m1.wordid <> m2.wordid
  GROUP BY m2.wordid
  ORDER BY count(m2.trackid) DESC, m2.wordid;


-- :name h2-select-artists :? :*
SELECT TOP :sql:top
  t.mxmartistname
FROM MSDTRACKS t
  INNER JOIN MATRIX m ON m.TRACKID = t.TRACKID
WHERE m.wordid IN (:v*:wordids)
GROUP BY t.mxmartistname
ORDER BY COUNT(DISTINCT m.wordid) DESC, HASH('SHA256', STRINGTOUTF8(t.MXMARTISTNAME), 1);

