import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { ROLE_LABELS, Role } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';

interface NavItem {
  label: string;
  icon: string;
  link: string;
  roles?: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { label: 'My claims', icon: 'dashboard', link: '/claimant', roles: ['CLAIMANT'] },
  { label: 'Report incident', icon: 'add_circle', link: '/claimant/report', roles: ['CLAIMANT'] },
  { label: 'Workspace', icon: 'work', link: '/officer', roles: ['CLAIMS_OFFICER'] },
  { label: 'Dashboard', icon: 'insights', link: '/manager', roles: ['MANAGER'] },
  { label: 'All claims', icon: 'assignment', link: '/claims', roles: ['CLAIMS_OFFICER', 'MANAGER', 'ADMIN'] },
  { label: 'Policies', icon: 'policy', link: '/policies' },
  { label: 'Notifications', icon: 'notifications', link: '/notifications' },
];

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(AuthService);

  protected readonly user = this.auth.currentUser;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe(Breakpoints.Handset)
      .pipe(map((result) => result.matches)),
    { initialValue: false },
  );
  protected readonly navItems = computed(() =>
    NAV_ITEMS.filter((item) => !item.roles || this.auth.hasRole(...item.roles)),
  );

  protected logout(): void {
    this.auth.logout();
  }
}
