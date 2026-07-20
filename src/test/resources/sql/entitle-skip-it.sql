-- Two disabled USER timers for mod-bar: one has a user id, the other does not. Used to verify that enabling the
-- module schedules the timer that carries a user id and skips (leaves disabled) the one that does not.
insert into test_mod_scheduler.timer(id, module_id, module_name, type, user_id, timer_descriptor)
values
    ('123e4567-e89b-12d3-a456-4266141740a0', 'mod-bar-1.0.0', 'mod-bar', 'USER',
     '00000000-0000-0000-0000-000000000000', '{
        "id": "123e4567-e89b-12d3-a456-4266141740a0",
        "enabled": "false",
        "type": "user",
        "moduleId": "mod-bar-1.0.0",
        "moduleName": "mod-bar",
        "routingEntry": {
           "methods": [ "POST" ],
           "pathPattern": "/testb/timer/bar-with-user",
           "schedule": { "cron": "*/5 * * * *" }
        }
       }'
    ),
    ('123e4567-e89b-12d3-a456-4266141740a1', 'mod-bar-1.0.0', 'mod-bar', 'USER', null, '{
        "id": "123e4567-e89b-12d3-a456-4266141740a1",
        "enabled": "false",
        "type": "user",
        "moduleId": "mod-bar-1.0.0",
        "moduleName": "mod-bar",
        "routingEntry": {
           "methods": [ "POST" ],
           "pathPattern": "/testb/timer/bar-without-user",
           "schedule": { "cron": "*/5 * * * *" }
        }
       }'
    );
