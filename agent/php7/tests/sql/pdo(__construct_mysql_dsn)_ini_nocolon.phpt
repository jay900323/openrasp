--TEST--
hook PDO::__construct (dsn) ini 
--SKIPIF--
<?php 
$conf = <<<CONF
security.enforce_policy: true
CONF;
include(__DIR__.'/../skipif.inc');
if (!extension_loaded("mysqli")) die("Skipped: mysqli extension required.");
if (!extension_loaded("pdo")) die("Skipped: pdo extension required.");
file_put_contents('/tmp/openrasp/mysql_connect', 'mysql:host=127.0.0.1;port=3306');
?>
--INI--
openrasp.root_dir=/tmp/openrasp
pdo.dsn.openrasp=nocolon
--FILE--
<?php
new PDO('openrasp.no', 'root');
?>
--EXPECTREGEX--
Fatal error: Uncaught .*PDOException.*