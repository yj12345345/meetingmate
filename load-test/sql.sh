#!/usr/bin/env bash
# Run SQL against the LOCAL stack's MySQL (meetingmate schema). Reads SQL from stdin.
docker exec -i meetingmate-obs-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" meetingmate -t 2>/dev/null'
