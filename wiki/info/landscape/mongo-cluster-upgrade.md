# MongoDB Cluster Upgrades

In our production environment on AWS, we currently (2026-02-10) run three MongoDB replica sets:

- ``live``: holds all databases for live operations and consists of three nodes: two i3.large instances with fast NVMe storage used for the ``/var/lib/mongo`` partition, and a hidden instance with an EBS volume that is backed up on a daily basis
- ``archive``: holds the ``winddb`` database used for the ARCHIVE server
- ``slow``: used for backing up databases when removing them from the ``live`` replica set, e.g., when shutting down an application replica set after an event

The ``archive`` and ``slow`` replica sets usually have only a single instance running on ``dbserver.internal.sapsailing.com``, and this is also where the hidden replica of the ``live`` replica set runs. The other two ``live`` nodes have internal DNS names set for them: ``mongo[01].internal.sapsailing.com``.

Upgrades may affect the packages installed on the nodes, or may affect the major version of MongoDB being run. Both upgrade procedures are described in the following two sections.

## Upgrade Using Package Manager

With Amazon Linux 2023, ``dnf`` is the package manager used. When logging on to an instance, a message like

```
A newer release of "Amazon Linux" is available.
  Version 2023.10.20260202:
Run "/usr/bin/dnf check-release-update" for full release and version update info
```

may be shown. In this case, run

```
dnf --releasever=latest upgrade
```

and watch closely what the package manager suggests. As soon as you see a kernel update about to install, displayed in red color (if your terminal supports colored output), a reboot will be required after completing the installation. This can also be checked using the following command:

```
needs-restarting -r
```

It will output a message like

```
No core libraries or services have been updated since boot-up.
Reboot should not be necessary.
```

and exits with code ``0`` if no reboot is required; otherwise, it will exit with ``1`` and display a corresponding message.

To avoid interrupting user-facing services, rebooting the MongoDB nodes shall follow a certain procedure:

- Ensure that no ARCHIVE candidate is currently launching; such a candidate would read from the ``archive`` replica set, so that rebooting the ``dbserver.internal.sapsailing.com`` node would interrupt this loading process. If an ARCHIVE candidate is launching, wait for the launch to finish.
- Ensure that no application replica set is currently being shut down with backing up its database. This backup would fail if the ``dbserver.internal.sapsailing.com`` node were restarted as it hosts the ``slow`` replica set used for the backup.
- ssh into ``ec2-user@dbserver.internal.sapsailing.com``
- There, run ``sudo dnf --releasever=latest upgrade`` and confirm with "yes"
- Assuming an update was installed that now requires a reboot, run ``sudo reboot``
- Wait until the instance is back up and running, you can ssh into it again, and ``pgrep mongod`` shows the three process IDs of the three running ``mongod`` processes
- ssh into ``ec2-user@mongo0.internal.sapsailing.com``
- run ``mongosh`` to see if ``mongo0`` is currently primary or secondary in the ``live`` replica set
- if you see "secondary", you're all set; if you see "primary", enter ``rs.stepDown()`` and see how the prompt changes from "primary" to "secondary"
- use ``quit()`` to exit the ``mongosh`` shell
- run ``sudo dnf --releasever=latest upgrade`` and confirm with "yes"
- if a reboot is required, run ``sudo reboot``
- wait for the instance and its ``mongod`` process to become available again; you may probe, e.g., by ssh-ing into the instance and checking with ``mongosh``
- repeat the process described for ``mongo0`` for ``mongo1.internal.sapsailing.com``

Hint: You can choose the order between ``mongo0`` and ``mongo1`` as you wish. If you start with the "secondary" instance, you will save one ``rs.stepDown()`` command.

## MongoDB Major Version Upgrade

