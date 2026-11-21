"use client";

export function Navbar({ onMyTickets }: { onMyTickets: () => void }) {
  return (
    <header className="nav">
      <div className="wrap nav-inner">
        <a className="brand" href="/">
          <span className="dot" />
          FRONT&nbsp;ROW
        </a>
        <nav className="nav-actions">
          <button className="btn btn-sm" onClick={onMyTickets}>
            My tickets
          </button>
        </nav>
      </div>
    </header>
  );
}
