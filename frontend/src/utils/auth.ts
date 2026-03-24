const ACCESS_TOKEN_KEY = "accessToken";
const POST_LOGIN_REDIRECT_KEY = "postLoginRedirect";
const DEFAULT_REDIRECT_PATH = "/home";

export const isAuthenticated = () => !!localStorage.getItem(ACCESS_TOKEN_KEY);

export const savePostLoginRedirect = (target: string) => {
    if (!target.startsWith("/")) {
        return;
    }
    localStorage.setItem(POST_LOGIN_REDIRECT_KEY, target);
};

export const getPostLoginRedirect = (redirectParam?: string | null) => {
    if (redirectParam && redirectParam.startsWith("/")) {
        return redirectParam;
    }

    const storedTarget = localStorage.getItem(POST_LOGIN_REDIRECT_KEY);
    if (storedTarget && storedTarget.startsWith("/")) {
        return storedTarget;
    }

    return DEFAULT_REDIRECT_PATH;
};

export const clearPostLoginRedirect = () => {
    localStorage.removeItem(POST_LOGIN_REDIRECT_KEY);
};

export const buildRedirectTarget = (
    pathname: string,
    search = "",
    hash = ""
) => `${pathname || DEFAULT_REDIRECT_PATH}${search}${hash}`;
