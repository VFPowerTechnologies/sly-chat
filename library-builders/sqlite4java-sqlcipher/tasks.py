class Task(object):
    "Simple task with dependencies"

    def __init__(self, name, description=None):
        self.name = name
        self.description = description
        self.depends_on = []

    def add_dependency(self, task_name):
        self.depends_on.append(task_name)

    def run(self, task_context):
        raise NotImplementedError()


class Tasks(object):
    "Very simple lists of tasks"

    def __init__(self):
        self.tasks_by_name = {}

    def add(self, task):
        if task.name in self.tasks_by_name:
            raise RuntimeError('A task with name `%s` already exists' % task.name)

        self.tasks_by_name[task.name] = task

    def _get_task_by_name(self, task_name):
        return self.tasks_by_name[task_name]

    def _run_task(self, task, tasks_completed, task_context):
        for dependency_name in task.depends_on:
            dependency = self._get_task_by_name(dependency_name)

            if dependency.name in tasks_completed:
                print('Task <<%s>> has already run, skipping' % dependency.name)
                continue

            self._run_task(dependency, tasks_completed, task_context)

            tasks_completed.add(dependency.name)

        print('Running task <<%s>>' % task.name)
        task.run(task_context)

    def print_tasks(self, indent_level=0):
        for task_name, task in self.tasks_by_name.items():
            print('%s%s: %s' % ('  '*indent_level, task_name, task.description))

    def run(self, task_name, task_context):
        task = self._get_task_by_name(task_name)

        tasks_completed = set()

        self._run_task(task, tasks_completed, task_context)
