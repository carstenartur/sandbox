-- init-db/01-create-database.sql
-- Creates the jgit database with case-sensitive collation for SHA-1 comparisons.

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'jgit')
BEGIN
    CREATE DATABASE jgit;
END
GO

USE jgit;
GO

-- Case-sensitive collation is important for SHA-1 hex string comparisons
ALTER DATABASE jgit COLLATE Latin1_General_CS_AS;
GO

-- Enable READ_COMMITTED_SNAPSHOT for better concurrency on ref updates
ALTER DATABASE jgit SET READ_COMMITTED_SNAPSHOT ON;
GO
