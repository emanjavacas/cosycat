(ns react-bootstrap.components
  (:require [reagent.core]
            [cljsjs.react-bootstrap])
  (:require-macros [react-bootstrap.adaptors :refer [bs-components]]))

(bs-components
 "Accordion"
 "Alert"
 "Badge"
 "Breadcrumb"
 "BreadcrumbItem"
 "Button"
 "ButtonGroup"
 "ButtonInput"
 "ButtonToolbar"
 "Carousel"
 "CarouselItem"
 "Col"
 "CollapsibleNav"
 "Dropdown"
 "DropdownButton"
 "Glyphicon"
 "Grid"
 "Image"
 "Input"
 "Interpolate"
 "Jumbotron"
 "Label"
 "ListGroup"
 "ListGroupItem"
 "MenuItem"
 "Modal"
 "ModalBody"
 "ModalFooter"
 "ModalHeader"
 "ModalTitle"
 "Nav"
 "Navbar"
 "NavBrand"
; "NavbarBrand" 
 "NavDropdown"
 "NavItem"
 "Overlay"
 "OverlayTrigger"
 "PageHeader"
 "PageItem"
 "Pager"
 "Pagination"
 "Panel"
 "PanelGroup"
 "Popover"
 "ProgressBar"
 "ResponsiveEmbed"
 "Row"
 "SafeAnchor"
 "SplitButton"
 "Tab"
 "Table"
 "Tabs"
 "Thumbnail"
 "Tooltip"
 "Well"
 "Collapse"
 "Fade")

(def dropdown-toggle
  (reagent.core/adapt-react-class js/ReactBootstrap.Dropdown.Toggle))

(def dropdown-menu
  (reagent.core/adapt-react-class js/ReactBootstrap.Dropdown.Menu))

(def navbar-collapse
  (reagent.core/adapt-react-class js/ReactBootstrap.Navbar.Collapse))

(def navbar-header
  (reagent.core/adapt-react-class js/ReactBootstrap.Navbar.Header))

(def navbar-brand
  (reagent.core/adapt-react-class js/ReactBootstrap.Navbar.Brand))

(def navbar-toggle
  (reagent.core/adapt-react-class js/ReactBootstrap.Navbar.Toggle))

(def navbar-link
  (reagent.core/adapt-react-class js/ReactBootstrap.Navbar.Link))

(def navbar-text
  (reagent.core/adapt-react-class js/ReactBootstrap.Navbar.Text))




