CREATE DATABASE IF NOT EXISTS graphdb;
USE graphdb;

-- Nodes table
CREATE TABLE IF NOT EXISTS nodes (
  node_id INT PRIMARY KEY,
  label VARCHAR(255) NOT NULL
);

-- Global PageRank results
CREATE TABLE IF NOT EXISTS pagerank (
  node_id INT NOT NULL,
  score DOUBLE NOT NULL,
  PRIMARY KEY (node_id),
  FOREIGN KEY (node_id) REFERENCES nodes(node_id) ON DELETE CASCADE
);

-- Personalized PageRank results
CREATE TABLE IF NOT EXISTS ppr (
  node_id INT NOT NULL,
  source_id INT NOT NULL,
  score DOUBLE NOT NULL,
  PRIMARY KEY (node_id, source_id),
  FOREIGN KEY (node_id) REFERENCES nodes(node_id) ON DELETE CASCADE
);

-- Sample nodes
INSERT INTO nodes (node_id, label) VALUES
  (1, 'Node 1'),
  (2, 'Node 2'),
  (3, 'Node 3'),
  (4, 'Node 4')
ON DUPLICATE KEY UPDATE label = VALUES(label);

-- Clear any existing PR/PPR data for a clean start
DELETE FROM pagerank;
DELETE FROM ppr;


