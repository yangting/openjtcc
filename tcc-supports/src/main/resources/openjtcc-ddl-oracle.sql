drop table TCC_TRANSACTION;
drop table TCC_COMPENSABLE;
drop table TCC_TERMINATOR;

create table TCC_TRANSACTION (
  application      VARCHAR2(60) not null,
  endpoint         VARCHAR2(60) not null,
  global_tx_id     VARCHAR2(36) not null,
  status           INTEGER default 0,
  status_trace     INTEGER default 0,
  coordinator      INTEGER default 0,
  created_time     TIMESTAMP(6),
  bizkey           VARCHAR2(36),
  deleted          INTEGER default 0,
  primary key (application, endpoint, global_tx_id)
);

create table TCC_COMPENSABLE (
  application        VARCHAR2(60) not null,
  endpoint           VARCHAR2(60) not null,
  global_tx_id       VARCHAR2(36) not null,
  branch_qualifier   VARCHAR2(20) not null,
  coordinator        INTEGER default 0,
  bean_name          VARCHAR2(30),
  variable           BLOB,
  try_committed   INTEGER default 0,
  confirmed          INTEGER default 0,
  cancelled          INTEGER default 0,
  committed          INTEGER default 0,
  rolledback         INTEGER default 0,
  primary key (application, endpoint, global_tx_id, branch_qualifier)
);

create table TCC_TERMINATOR (
  application    VARCHAR2(60) not null,
  endpoint       VARCHAR2(60) not null,
  global_tx_id   VARCHAR2(36) not null,
  to_application VARCHAR2(60) not null,
  to_endpoint    VARCHAR2(60) not null,
  prepared       INTEGER default 0,
  committed      INTEGER default 0,
  rolledback     INTEGER default 0,
  cleanup        INTEGER default 0,
  primary key (application, endpoint, global_tx_id, to_application, to_endpoint)
);
