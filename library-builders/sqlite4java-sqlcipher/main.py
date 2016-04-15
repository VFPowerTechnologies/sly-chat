import sys

from tasks import Tasks
from config import read_context_from_config
from build import (CreateArchDirsTask, CreateWorkDirsTask, BuildOpenSSLTask,
                   BuildSQLCipher, BuildSQLite4JavaTask, add_download_tasks)


def get_tasks():
    tasks = Tasks()
    tasks.add(CreateWorkDirsTask())
    tasks.add(CreateArchDirsTask())
    add_download_tasks(tasks)
    tasks.add(BuildOpenSSLTask())
    tasks.add(BuildSQLCipher())
    tasks.add(BuildSQLite4JavaTask())

    return tasks

def main():
    tasks = get_tasks()
    if len(sys.argv) < 3:
        print('Usage: main.py /path/to/config.conf task_name')
        print('Available tasks:')
        tasks.print_tasks(1)
        return

    config_path = sys.argv[1]
    task_name_to_run = sys.argv[2]

    context = read_context_from_config(config_path)
    tasks.run(task_name_to_run, context)


if __name__ == '__main__':
    main()

