import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { ProtectedRoute, PublicOnlyRoute } from "./ProtectedRoute";
import { useAuth } from "./useAuth";
import type { AuthContextValue } from "./AuthContext";

vi.mock("./useAuth", () => ({ useAuth: vi.fn() }));

function mockAuth(overrides: Partial<AuthContextValue>) {
  vi.mocked(useAuth).mockReturnValue({
    user: null,
    isInitializing: false,
    isAuthenticated: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
    ...overrides,
  });
}

function renderProtected(initialPath = "/dashboard") {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/login" element={<div>Login page</div>} />
        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<div>Dashboard content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  it("shows a spinner while the session is still being restored", () => {
    mockAuth({ isInitializing: true });
    renderProtected();
    expect(screen.getByRole("status", { name: "Loading" })).toBeInTheDocument();
    expect(screen.queryByText("Dashboard content")).not.toBeInTheDocument();
  });

  it("redirects to /login when there is no authenticated user", () => {
    mockAuth({ isInitializing: false, isAuthenticated: false });
    renderProtected();
    expect(screen.getByText("Login page")).toBeInTheDocument();
  });

  it("renders the nested route once authenticated", () => {
    mockAuth({ isInitializing: false, isAuthenticated: true });
    renderProtected();
    expect(screen.getByText("Dashboard content")).toBeInTheDocument();
  });
});

describe("PublicOnlyRoute", () => {
  function renderPublicOnly() {
    return render(
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route path="/" element={<div>Home content</div>} />
          <Route element={<PublicOnlyRoute />}>
            <Route path="/login" element={<div>Login form</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
  }

  it("shows the login form for a signed-out visitor", () => {
    mockAuth({ isAuthenticated: false });
    renderPublicOnly();
    expect(screen.getByText("Login form")).toBeInTheDocument();
  });

  it("bounces an already-signed-in user away from /login", () => {
    mockAuth({ isAuthenticated: true });
    renderPublicOnly();
    expect(screen.getByText("Home content")).toBeInTheDocument();
    expect(screen.queryByText("Login form")).not.toBeInTheDocument();
  });
});
