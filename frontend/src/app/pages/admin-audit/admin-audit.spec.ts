import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminAuditComponent } from './admin-audit';

describe('AdminAuditComponent', () => {
  let component: AdminAuditComponent;
  let fixture: ComponentFixture<AdminAuditComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminAuditComponent, HttpClientTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminAuditComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('starts in loading state', () => {
    expect(component.loading()).toBeTrue();
  });

  it('shows entries after fetch completes', () => {
    const mockEntries = [
      {
        id: 1,
        createdAt: '2026-07-13T10:00:00',
        actorId: 1,
        actorEmail: 'u@test.com',
        actorRole: 'ADMIN',
        actionType: 'LOGIN',
        targetType: 'SESSION',
        targetId: null,
        severity: 'INFO',
        result: 'SUCCESS',
        payload: '{}',
        ipAddress: '192.168.1.1',
        userAgent: 'TestBrowser',
        correlationId: 'corr-1',
      },
    ];

    const req = httpMock.expectOne((r) => r.url === '/api/admin/audit');
    req.flush({
      items: mockEntries,
      currentPage: 0,
      totalPages: 1,
      totalItems: 1,
    });

    expect(component.loading()).toBeFalse();
    expect(component.entries().length).toBe(1);
    expect(component.entries()[0].actorEmail).toBe('u@test.com');
  });

  it('shows error state on HTTP error', () => {
    const req = httpMock.expectOne('/api/admin/audit');
    req.error(new ProgressEvent('error'));

    expect(component.error()).toBeTrue();
    expect(component.loading()).toBeFalse();
  });

  it('opens drawer when row is clicked', () => {
    const mockEntries = [
      {
        id: 1,
        createdAt: '2026-07-13T10:00:00',
        actorId: 1,
        actorEmail: 'u@test.com',
        actorRole: 'ADMIN',
        actionType: 'LOGIN',
        targetType: 'SESSION',
        targetId: null,
        severity: 'INFO',
        result: 'SUCCESS',
        payload: '{}',
        ipAddress: '192.168.1.1',
        userAgent: 'TestBrowser',
        correlationId: 'corr-1',
      },
    ];

    const req = httpMock.expectOne('/api/admin/audit');
    req.flush({
      items: mockEntries,
      currentPage: 0,
      totalPages: 1,
      totalItems: 1,
    });

    component.openDrawer(component.entries()[0]);
    expect(component.drawerOpen()).toBeTrue();
    expect(component.selectedEntry()).not.toBeNull();
  });

  it('closes drawer', () => {
    component.closeDrawer();
    expect(component.drawerOpen()).toBeFalse();
  });

  it('sets period and reloads on period change', () => {
    const req1 = httpMock.expectOne('/api/admin/audit');
    req1.flush({ items: [], currentPage: 0, totalPages: 0, totalItems: 0 });

    component.setPeriod('1m');
    expect(component.period()).toBe('1m');
    httpMock.expectOne((r) => r.url === '/api/admin/audit');
  });
});
